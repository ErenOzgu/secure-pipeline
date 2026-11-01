package io.github.erenozgu.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Puts a {@link Handler} behind the JDK's built-in HTTP server. */
public final class Server {

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    /** Something that can answer a request. */
    public interface Handler {
        App.Response handle(String method, String path, String rawQuery);
    }

    private Server() {}

    /**
     * Starts a server on the given port; port 0 picks a free one, which {@code getAddress()} then reports.
     */
    public static HttpServer start(int port, Handler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> serve(exchange, handler));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    private static void serve(HttpExchange exchange, Handler handler) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            App.Response response;
            try {
                response = handler.handle(method, uri.getPath(), uri.getRawQuery());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "a request failed", e);
                response = App.internalError();
            }
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(response.status(), -1);
            } else {
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
            }
        }
    }
}
