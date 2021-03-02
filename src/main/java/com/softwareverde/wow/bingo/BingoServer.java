package com.softwareverde.wow.bingo;

import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.endpoint.WebSocketEndpoint;
import com.softwareverde.http.server.servlet.DirectoryServlet;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class BingoServer {
    protected final HttpServer _apiServer = new HttpServer();
    protected final CachedThreadPool _threadPool = new CachedThreadPool(512, 1000L);
    protected final WebSocketApi _webSocketApi;

    protected final BingoState _bingoState;

    public BingoServer(final List<String> bingoSquares, final Long seed) {
        _apiServer.setPort(8080);

        _bingoState = new BingoState(bingoSquares, seed);
        _webSocketApi = new WebSocketApi(_bingoState);

        { // Api Endpoints
            final String apiRootPath = "/api";

            { // Api v1
                final String v1ApiPrePath = (apiRootPath + "/v1");

                { // WebSocket
                    final WebSocketEndpoint endpoint = new WebSocketEndpoint(_webSocketApi);
                    endpoint.setPath((v1ApiPrePath + "/websocket"));
                    endpoint.setStrictPathEnabled(true);
                    _apiServer.addEndpoint(endpoint);
                }
            }
        }

        { // Static Content
            final File servedDirectory = new File("www");
            final DirectoryServlet indexServlet = new DirectoryServlet(servedDirectory);
            indexServlet.setShouldServeDirectories(true);
            indexServlet.setIndexFile("index.html");
            indexServlet.setCacheEnabled(TimeUnit.DAYS.toSeconds(1L));

            final Endpoint endpoint = new Endpoint(indexServlet);
            endpoint.setPath("/");
            endpoint.setStrictPathEnabled(false);
            _apiServer.addEndpoint(endpoint);
        }
    }

    public void start() {
        _threadPool.start();
        _apiServer.start();
        _webSocketApi.start();
    }

    public void stop() {
        _webSocketApi.stop();
        _apiServer.stop();
        _threadPool.stop();
    }

    public void loop() {
        while (! Thread.interrupted()) {
            try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
        }
    }
}