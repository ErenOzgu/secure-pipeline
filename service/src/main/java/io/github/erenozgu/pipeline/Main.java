package io.github.erenozgu.pipeline;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.util.logging.Logger;

/** Starts the service. The port comes from the PORT environment variable, 8080 by default. */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = port(System.getenv("PORT"));
        HttpServer server = Server.start(port, new App(Main.class.getPackage().getImplementationVersion()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        LOG.info(() -> "listening on port " + server.getAddress().getPort());
    }

    /** The port to listen on: 8080 when unset, otherwise a whole number from 1 to 65535. */
    static int port(String value) {
        if (value == null || value.isBlank()) {
            return 8080;
        }
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a whole number from 1 to 65535, not '" + value + "'", e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("PORT must be a whole number from 1 to 65535, not " + port);
        }
        return port;
    }
}
