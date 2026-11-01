package io.github.erenozgu.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class ServerTest {

    private static HttpResponse<String> send(HttpServer server, String method, String target)
            throws IOException, InterruptedException {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + target);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void answersOverARealSocketWithTheHeaders() throws Exception {
        HttpServer server = Server.start(0, new App("1.0.0"));
        try {
            HttpResponse<String> response = send(server, "GET", "/hello?name=Ada");
            assertEquals(200, response.statusCode());
            assertEquals("hello, Ada\n", response.body());
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
            assertFalse(response.headers().firstValue("Server").isPresent());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aHeadRequestGetsHeadersAndNoBody() throws Exception {
        HttpServer server = Server.start(0, new App("1.0.0"));
        try {
            HttpResponse<String> response = send(server, "HEAD", "/healthz");
            assertEquals(200, response.statusCode());
            assertEquals("", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aWrongMethodIsRefused() throws Exception {
        HttpServer server = Server.start(0, new App("1.0.0"));
        try {
            assertEquals(405, send(server, "POST", "/healthz").statusCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aFailureInsideTheHandlerIsAnEmptyHandedFiveHundred() throws Exception {
        HttpServer server = Server.start(0, (method, path, query) -> {
            throw new IllegalStateException("secret detail that must not leak");
        });
        try {
            HttpResponse<String> response = send(server, "GET", "/anything");
            assertEquals(500, response.statusCode());
            assertEquals("internal error\n", response.body());
        } finally {
            server.stop(0);
        }
    }
}
