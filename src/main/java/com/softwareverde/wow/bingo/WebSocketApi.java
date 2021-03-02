package com.softwareverde.wow.bingo;

import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.http.server.servlet.WebSocketServlet;
import com.softwareverde.http.server.servlet.request.WebSocketRequest;
import com.softwareverde.http.server.servlet.response.WebSocketResponse;
import com.softwareverde.http.websocket.WebSocket;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;

import java.util.HashMap;
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
    protected static final HashMap<Long, String> USERNAMES = new HashMap<>();

    protected static final AtomicLong _nextSocketId = new AtomicLong(1L);

    protected volatile Boolean _isShuttingDown = false;

    protected final BingoState _bingoState;

    public WebSocketApi(final BingoState bingoState) {
        _bingoState = bingoState;
    }

    protected final CachedThreadPool _threadPool = new CachedThreadPool(256, 1000L);

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

    protected void _handleGetLabels(final Json request, final WebSocket webSocket) {
        final Integer requestId = request.getInteger("requestId");

        final Json squaresJson = new Json(true);
        for (final String squareLabel : _bingoState.getSquareLabels()) {
            squaresJson.add(squareLabel);
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("labels", squaresJson);

        webSocket.sendMessage(responseJson.toString());
    }

    protected void _handleGetGameState(final Json request, final WebSocket webSocket) {
        final Long webSocketId = webSocket.getId();

        final Integer requestId = request.getInteger("requestId");
        final Json parameters = request.get("parameters");
        final String username = parameters.getString("username").toLowerCase();

        final BingoGame bingoGame;

        WRITE_LOCK.lock();
        try {
            if (! _bingoState.hasBingoGame(username)) {
                _bingoState.newBingoGame(username);
            }
            bingoGame = _bingoState.getBingoGame(username);
            USERNAMES.put(webSocketId, username);
        }
        finally {
            WRITE_LOCK.unlock();
        }

        final Json responseJson = new Json();
        responseJson.put("requestId", requestId);
        responseJson.put("wasSuccess", 1);
        responseJson.put("gameState", bingoGame);

        webSocket.sendMessage(responseJson.toString());
    }

    protected void _broadcastGameState() {
        READ_LOCK.lock();
        try {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                final Long webSocketId = webSocket.getId();
                final String username = USERNAMES.get(webSocketId);
                if (username == null) { continue; }

                final BingoGame bingoGame = _bingoState.getBingoGame(username);

                final Json responseJson = new Json();
                responseJson.put("requestId", null);
                responseJson.put("wasSuccess", 1);
                responseJson.put("gameState", bingoGame);

                webSocket.sendMessage(responseJson.toString());
            }
        }
        finally {
            READ_LOCK.unlock();
        }
    }

    protected void _handleGetGlobalGameState(final Json request, final WebSocket webSocket) {
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

        webSocket.sendMessage(responseJson.toString());
    }

    protected void _handleUpdateGlobalGameState(final Json request, final WebSocket webSocket) {
        final Integer requestId = request.getInteger("requestId");
        final Json parameters = request.get("parameters");

        WRITE_LOCK.lock();
        try { // Update the global game state...
            final Integer index = parameters.getInteger("index");
            final Boolean isMarked = parameters.getBoolean("isMarked");
            _bingoState.markLabel(index, isMarked);
        }
        finally {
            WRITE_LOCK.unlock();
        }

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

        webSocket.sendMessage(responseJson.toString());

        _broadcastGameState();
    }

    protected void _onMessage(final Json request, final WebSocket webSocket) {
        final String query = request.getString("query");
        switch (query) {
            case "getLabels": {
                _handleGetLabels(request, webSocket);
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

            default: {
                final Integer requestId = request.getInteger("requestId");

                final Json responseJson = new Json();
                responseJson.put("requestId", requestId);
                responseJson.put("wasSuccess", 0);
                responseJson.put("errorMessage", "Unknown query: " + query);

                webSocket.sendMessage(responseJson.toString());
            }
        }
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
            Logger.debug("Adding WebSocket: " + webSocketId + " (count=" + (WEB_SOCKETS.size() + 1) + ")");
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
                    Logger.debug("WebSocket Closed: " + webSocketId + " (count=" + (WEB_SOCKETS.size() - 1) + ")");
                    WEB_SOCKETS.remove(webSocketId);
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
    }

    public void stop() {
        _isShuttingDown = true;

        _threadPool.stop();

        WRITE_LOCK.lock();
        try {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                webSocket.close();
            }
            WEB_SOCKETS.clear();
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }
}
