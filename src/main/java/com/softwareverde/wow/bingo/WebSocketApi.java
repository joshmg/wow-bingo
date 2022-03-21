package com.softwareverde.wow.bingo;

import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.http.server.servlet.WebSocketServlet;
import com.softwareverde.http.server.servlet.request.WebSocketRequest;
import com.softwareverde.http.server.servlet.response.WebSocketResponse;
import com.softwareverde.http.websocket.WebSocket;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class WebSocketApi implements WebSocketServlet {
    protected static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    protected static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        READ_LOCK = readWriteLock.readLock();
        WRITE_LOCK = readWriteLock.writeLock();
    }

    protected static final HashMap<Long, WebSocket> WEB_SOCKETS = new HashMap<>();
    protected static final WeakHashMap<Long, WebSocket> ADMIN_WEB_SOCKETS = new WeakHashMap<>();
    protected static final HashMap<Long, String> USERNAMES = new HashMap<>();

    protected static final AtomicLong _nextSocketId = new AtomicLong(1L);

    protected volatile Boolean _isShuttingDown = false;

    protected final BingoState _bingoState;
    protected final String _adminPassword;

    public WebSocketApi(final BingoState bingoState, final String adminPassword) {
        _bingoState = bingoState;
        _adminPassword = adminPassword;
    }

    protected final CachedThreadPool _threadPool = new CachedThreadPool(256, 1000L);

    protected final Thread _pingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            thread.setName("WebSocket Ping Thread");
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.error(exception);
                }
            });

            while (! thread.isInterrupted()) {
                try { Thread.sleep(10000L); }
                catch (final InterruptedException exception) { break; }

                final String message;
                {
                    final Long nonce = (long) (Math.random() * Integer.MAX_VALUE);
                    final Json pingMessage = new Json(false);
                    pingMessage.put("ping", nonce);
                    message = pingMessage.toString();
                }

                READ_LOCK.lock();
                try {
                    for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                        webSocket.sendMessage(message);
                    }
                }
                finally {
                    READ_LOCK.unlock();
                }
            }
        }
    });

    protected Json _createGlobalGameStateJson() {
        final Json markedIndexes = new Json(false);
        int index = 0;
        for (final String label : _bingoState.getSquareLabels()) {
            final Boolean isMarked = _bingoState.isLabelMarked(index);
            markedIndexes.put(String.valueOf(index), isMarked);
            index += 1;
        }
        return markedIndexes;
    }

    protected Json _createPlayersJson() {
        final Json playersJson = new Json(true);
        for (final String playerName : _bingoState.getPlayers()) {
            final Boolean hasPaid = _bingoState.hasPaid(playerName);

            final Json playerJson = new Json(false);
            playerJson.put("name", playerName);
            playerJson.put("hasPaid", hasPaid);

            playersJson.add(playerJson);
        }
        return playersJson;
    }

    protected void _handleGetLabels(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetLabels " + webSocket.getId() + " " + request);
        final Integer requestId = request.getInteger("requestId");

        final Json squaresJson = new Json(true);
        for (final String squareLabel : _bingoState.getSquareLabels()) {
            squaresJson.add(squareLabel);
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("labels", squaresJson);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _handleGetPlayers(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetPlayers " + webSocket.getId() + " " + request);
        final Integer requestId = request.getInteger("requestId");

        final Json playersJson;
        READ_LOCK.lock();
        try {
            playersJson = _createPlayersJson();
        }
        finally {
            READ_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("players", playersJson);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _handleGetWinners(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetWinners " + webSocket.getId() + " " + request);
        final Integer requestId = request.getInteger("requestId");

        final Json winnersJson;
        READ_LOCK.lock();
        try {
            final List<String> winners = _bingoState.getWinningBingoUsers();

            winnersJson = new Json(true);
            for (final String winner : winners) {
                winnersJson.add(winner);
            }
        }
        finally {
            READ_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("winners", winnersJson);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _handleGetJackpot(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetJackpot " + webSocket.getId() + " " + request);

        final Integer requestId = request.getInteger("requestId");

        final Long jackpot;

        READ_LOCK.lock();
        try {
            jackpot = _bingoState.getJackpot();
        }
        finally {
            READ_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("jackpot", jackpot);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _broadcastPlayerList() {
        READ_LOCK.lock();
        try {
            final String message;
            {
                final Json playersJson = _createPlayersJson();
                final Json responseJson = new Json();
                responseJson.put("wasSuccess", 1);
                responseJson.put("players", playersJson);

                message = responseJson.toString();
            }

            for (final WebSocket webSocket : ADMIN_WEB_SOCKETS.values()) {
                if (webSocket == null) { continue; }
                _webSocketSendMessage(webSocket, message);
            }
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    protected void _handleGetGameState(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetGameState " + webSocket.getId() + " " + request);
        final Long webSocketId = webSocket.getId();

        final Integer requestId = request.getInteger("requestId");
        final Json parameters = request.get("parameters");
        final String username = parameters.getString("username").toLowerCase();

        final Long jackpot;
        final BingoGame bingoGame;
        final boolean userWasCreated;

        WRITE_LOCK.lock();
        try {
            if (! _bingoState.hasBingoGame(username)) {
                Logger.info("Creating BingoGame for: " + username);
                _bingoState.newBingoGame(username);
                userWasCreated = true;
            }
            else {
                userWasCreated = false;
            }

            bingoGame = _bingoState.getBingoGame(username);
            USERNAMES.put(webSocketId, username);

            jackpot = _bingoState.getJackpot();
        }
        finally {
            WRITE_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("gameState", bingoGame);
        responseJson.put("jackpot", jackpot);

        if (userWasCreated) {
            _broadcastPlayerList();
        }

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _broadcastGameState() {
        Logger.trace("_broadcastGameState");
        READ_LOCK.lock();
        try {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                final Long webSocketId = webSocket.getId();
                final String username = USERNAMES.get(webSocketId);
                if (username == null) { continue; }

                final Long jackpot = _bingoState.getJackpot();
                final BingoGame bingoGame = _bingoState.getBingoGame(username);

                final Json responseJson = new Json();
                responseJson.put("requestId", null);
                responseJson.put("wasSuccess", 1);
                responseJson.put("gameState", bingoGame);
                responseJson.put("jackpot", jackpot);

                _webSocketSendMessage(webSocket, responseJson.toString());
            }
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    protected Json _createWinnersJson() {
        final List<String> bingoWinners = _bingoState.getWinningBingoUsers();
        final Json winnersJson = new Json(true);

        int i = 0;
        for (final String username : bingoWinners) {
            final Long amount = _bingoState.getJackpot(i);

            final Json playerJson = new Json(false);
            playerJson.put("name", username);
            playerJson.put("amount", amount);

            winnersJson.add(playerJson);

            i += 1;
        }

        return winnersJson;
    }

    protected void _broadcastBingoWinners() {
        Logger.trace("_broadcastBingoWinners");

        READ_LOCK.lock();
        try {
            final Json winnersJson = _createWinnersJson();

            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                final Json responseJson = new Json();
                responseJson.put("wasSuccess", 1);
                responseJson.put("bingoWinners", winnersJson);

                _webSocketSendMessage(webSocket, responseJson.toString());
            }
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    protected void _registerAdminWebSocket(final WebSocket webSocket) {
        WRITE_LOCK.lock();
        try {
            final Long webSocketId = webSocket.getId();
            ADMIN_WEB_SOCKETS.put(webSocketId, webSocket);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    protected synchronized void _handleGetGlobalGameState(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetGlobalGameState " + webSocket.getId() + " " + request);
        final Json parameters = request.get("parameters");
        final String password = parameters.getString("password");
        if (! Util.areEqual(_adminPassword, password)) {
            _sendUnauthorizedRequest(request, webSocket);
            return;
        }

        _registerAdminWebSocket(webSocket);

        final Integer requestId = request.getInteger("requestId");

        final Json globalGameStateJson;
        READ_LOCK.lock();
        try {
            globalGameStateJson = _createGlobalGameStateJson();
        }
        finally {
            READ_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("globalGameState", globalGameStateJson);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected synchronized void _handleUpdateGlobalGameState(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleUpdateGlobalGameState " + webSocket.getId() + " " + request);
        final Integer requestId = request.getInteger("requestId");
        final Json parameters = request.get("parameters");
        final String password = parameters.getString("password");
        if (! Util.areEqual(_adminPassword, password)) {
            _sendUnauthorizedRequest(request, webSocket);
            return;
        }

        _registerAdminWebSocket(webSocket);

        final Integer index = parameters.getInteger("index");
        final Boolean isMarked = parameters.getBoolean("isMarked");

        final boolean hasNewBingoWinner;
        final Json globalGameStateJson;

        WRITE_LOCK.lock();
        try { // Update the global game state...
            final List<String> originalWinningBingoUsers = _bingoState.getWinningBingoUsers();
            _bingoState.markLabel(index, isMarked);
            final List<String> newWinningBingoUsers = _bingoState.getWinningBingoUsers();

            globalGameStateJson = _createGlobalGameStateJson();
            hasNewBingoWinner = (newWinningBingoUsers.getCount() > originalWinningBingoUsers.getCount());
        }
        finally {
            WRITE_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("globalGameState", globalGameStateJson);

        _webSocketSendMessage(webSocket, responseJson.toString());

        _broadcastGameState();

        if (hasNewBingoWinner) {
            if (Logger.isInfoEnabled()) {
                READ_LOCK.lock();
                try {
                    final List<String> bingoWinners = _bingoState.getWinningBingoUsers();
                    final String[] bingoWinnersArray = new String[bingoWinners.getCount()];
                    for (int i = 0; i < bingoWinnersArray.length; ++i) {
                        bingoWinnersArray[i] = bingoWinners.get(i);
                    }

                    Logger.info("Bingo Winners: " + Util.join(", ", bingoWinnersArray));
                }
                finally {
                    READ_LOCK.unlock();
                }
            }

            _broadcastBingoWinners();
        }
    }

    protected synchronized void _handleSetHasPaid(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleSetHasPaid " + webSocket.getId() + " " + request);
        final Integer requestId = request.getInteger("requestId");
        final Json parameters = request.get("parameters");
        final String password = parameters.getString("password");
        if (! Util.areEqual(_adminPassword, password)) {
            _sendUnauthorizedRequest(request, webSocket);
            return;
        }

        _registerAdminWebSocket(webSocket);

        WRITE_LOCK.lock();
        try { // Update the global game state...
            final String username = parameters.getString("username").toLowerCase();
            final Boolean hasPaid = parameters.getBoolean("hasPaid");

            _bingoState.setHasPaid(username, hasPaid);
            Logger.info("Set " + username + " paid=" + hasPaid);
        }
        finally {
            WRITE_LOCK.unlock();
        }

        final Json playersJson;
        READ_LOCK.lock();
        try {
            playersJson = _createPlayersJson();
        }
        finally {
            READ_LOCK.unlock();
        }

        _broadcastGameState();

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("players", playersJson);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _sendUnauthorizedRequest(final Json request, final WebSocket webSocket) {
        Logger.trace("_sendUnauthorizedRequest " + webSocket.getId() + " " + request);

        final Integer requestId = request.getInteger("requestId");

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 0);
        responseJson.put("errorMessage", "Unauthorized.");

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _handleGetBingoWinners(final Json request, final WebSocket webSocket) {
        Logger.trace("_handleGetBingoWinners " + webSocket.getId() + " " + request);

        final Json winnersJson;
        READ_LOCK.lock();
        try {
            winnersJson = _createWinnersJson();
        }
        finally {
            READ_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", null);
        responseJson.put("wasSuccess", 1);
        responseJson.put("bingoWinners", winnersJson);

        _webSocketSendMessage(webSocket, responseJson.toString());
    }

    protected void _onMessage(final Json request, final WebSocket webSocket) {
        final String query = request.getString("query");

        switch (query) {
            case "getLabels": {
                _handleGetLabels(request, webSocket);
            } break;

            case "getJackpot": {
                _handleGetJackpot(request, webSocket);
            } break;

            case "getPlayers": {
                _handleGetPlayers(request, webSocket);
            } break;

            case "getWinners": {
                _handleGetWinners(request, webSocket);
            } break;

            case "getGameState": {
                _handleGetGameState(request, webSocket);
            } break;

            case "getGlobalGameState": {
                _handleGetGlobalGameState(request, webSocket);
            } break;

            case "updateGlobalGameState": {
                _handleUpdateGlobalGameState(request, webSocket);
            } break;

            case "setHasPaid": {
                _handleSetHasPaid(request, webSocket);
            } break;

            case "getBingoWinners": {
                _handleGetBingoWinners(request, webSocket);
            } break;

            default: {
                final Integer requestId = request.getInteger("requestId");

                final Json responseJson = new Json();
                responseJson.put("requestId", requestId);
                responseJson.put("wasSuccess", 0);
                responseJson.put("errorMessage", "Unknown query: " + query);

                _webSocketSendMessage(webSocket, responseJson.toString());
            }
        }
    }

    protected void _webSocketSendMessage(final WebSocket webSocket, final String message) {
        Logger.trace("SENDING: " + webSocket.getId() + " " + message);
        webSocket.sendMessage(message);
    }

    @Override
    public WebSocketResponse onRequest(final WebSocketRequest webSocketRequest) {
        final WebSocketResponse webSocketResponse = new WebSocketResponse();
        if (! _isShuttingDown) {
            final Long webSocketId = _nextSocketId.getAndIncrement();
            webSocketResponse.setWebSocketId(webSocketId);
            webSocketResponse.upgradeToWebSocket();
        }
        return webSocketResponse;
    }

    @Override
    public void onNewWebSocket(final WebSocket webSocket) {
        if (_isShuttingDown) {
            webSocket.close();
            return;
        }

        final Long webSocketId = webSocket.getId();
        WRITE_LOCK.lock();
        try {
            WEB_SOCKETS.put(webSocketId, webSocket);
        }
        finally {
            WRITE_LOCK.unlock();
        }

        webSocket.setMessageReceivedCallback(new WebSocket.MessageReceivedCallback() {
            @Override
            public void onMessage(final String request) {
                try {
                    final Json requestJson = Json.parse(request);
                    _onMessage(requestJson, webSocket);
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
            }
        });

        webSocket.setConnectionClosedCallback(new WebSocket.ConnectionClosedCallback() {
            @Override
            public void onClose(final int code, final String message) {
                WRITE_LOCK.lock();
                try {
                    WEB_SOCKETS.remove(webSocketId);
                    USERNAMES.remove(webSocketId);
                }
                finally {
                    WRITE_LOCK.unlock();
                }
            }
        });

        webSocket.startListening();
    }

    public void start() {
        _threadPool.start();
        _pingThread.start();
    }

    public void stop() {
        _isShuttingDown = true;

        _threadPool.stop();
        _pingThread.interrupt();

        WRITE_LOCK.lock();
        try {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                webSocket.close();
            }
            WEB_SOCKETS.clear();
            USERNAMES.clear();
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }
}
