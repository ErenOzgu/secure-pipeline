package io.github.erenozgu.pipeline;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the service does, as a function: a request goes in, a response comes out.
 *
 * <p>The HTTP plumbing lives in {@link Server}, so everything that matters here can be tested without a socket.
 */
public final class App implements Server.Handler {

    /** The longest name the service will greet. */
    static final int MAX_NAME_LENGTH = 64;

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} ._-]+");
    private static final Pattern SAFE_VERSION = Pattern.compile("[0-9A-Za-z.+_-]{1,32}");

    /** One response: status, headers and a text body. */
    public record Response(int status, Map<String, String> headers, String body) {}

    private final String version;

    /**
     * @param version the version to report; anything that is not a plain version string is reported as "unknown",
     *     and {@code null} (running from classes, not from the jar) as "dev"
     */
    public App(String version) {
        if (version == null) {
            this.version = "dev";
        } else {
            this.version = SAFE_VERSION.matcher(version).matches() ? version : "unknown";
        }
    }

    @Override
    public Response handle(String method, String path, String rawQuery) {
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            return respond(405, "text/plain; charset=utf-8", "method not allowed\n", Map.of("Allow", "GET, HEAD"));
        }
        return switch (path) {
            case "/healthz" -> respond(200, "text/plain; charset=utf-8", "ok\n", Map.of());
            case "/version" -> respond(200, "application/json", "{\"version\":\"" + version + "\"}\n", Map.of());
            case "/hello" -> hello(rawQuery);
            default -> respond(404, "text/plain; charset=utf-8", "not found\n", Map.of());
        };
    }

    /** The answer for a request that failed inside the service. It says nothing about why. */
    public static Response internalError() {
        return respond(500, "text/plain; charset=utf-8", "internal error\n", Map.of());
    }

    private static Response hello(String rawQuery) {
        String name = queryParameter(rawQuery, "name").orElse("world");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || !NAME.matcher(name).matches()) {
            return respond(400, "text/plain; charset=utf-8", "invalid name\n", Map.of());
        }
        return respond(200, "text/plain; charset=utf-8", "hello, " + name + "\n", Map.of());
    }

    /** The first value of a query parameter. A value that is not valid percent-encoding comes back empty. */
    static Optional<String> queryParameter(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Optional.empty();
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String rawKey = eq < 0 ? pair : pair.substring(0, eq);
            if (!key.equals(decode(rawKey))) {
                continue;
            }
            return Optional.of(eq < 0 ? "" : decode(pair.substring(eq + 1)));
        }
        return Optional.empty();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Every response carries the same defensive headers; a service that forgets them on one path is the usual finding. */
    private static Response respond(int status, String contentType, String body, Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        headers.put("Referrer-Policy", "no-referrer");
        headers.put("Permissions-Policy", "camera=(), geolocation=(), microphone=()");
        headers.put("Cross-Origin-Opener-Policy", "same-origin");
        headers.put("Cross-Origin-Embedder-Policy", "require-corp");
        headers.put("Cross-Origin-Resource-Policy", "same-origin");
        headers.putAll(extra);
        return new Response(status, Collections.unmodifiableMap(headers), body);
    }
}
