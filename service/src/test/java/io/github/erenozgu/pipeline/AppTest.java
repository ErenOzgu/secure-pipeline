package io.github.erenozgu.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppTest {

    private final App app = new App("1.2.3");

    /** Headers that every response must carry, whatever the path or the outcome. */
    private static final List<String> DEFENSIVE_HEADERS = List.of(
            "Cache-Control",
            "X-Content-Type-Options",
            "Content-Security-Policy",
            "Referrer-Policy",
            "Permissions-Policy",
            "Cross-Origin-Opener-Policy",
            "Cross-Origin-Embedder-Policy",
            "Cross-Origin-Resource-Policy");

    @Test
    void healthzSaysOk() {
        App.Response response = app.handle("GET", "/healthz", null);
        assertEquals(200, response.status());
        assertEquals("ok\n", response.body());
    }

    @Test
    void helloGreetsTheWorldByDefault() {
        assertEquals("hello, world\n", app.handle("GET", "/hello", null).body());
    }

    @Test
    void helloGreetsTheNameItIsGiven() {
        assertEquals("hello, Ada\n", app.handle("GET", "/hello", "name=Ada").body());
    }

    @Test
    void helloDecodesPercentEncodingAndKeepsLetters() {
        assertEquals("hello, Çağrı Ö\n", app.handle("GET", "/hello", "name=%C3%87a%C4%9Fr%C4%B1+%C3%96").body());
    }

    @Test
    void helloUsesTheFirstNameWhenThereAreTwo() {
        assertEquals("hello, one\n", app.handle("GET", "/hello", "x=1&name=one&name=two").body());
    }

    @Test
    void helloIgnoresOtherParameters() {
        assertEquals("hello, world\n", app.handle("GET", "/hello", "other=1").body());
    }

    @Test
    void helloRefusesNamesThatAreNotPlainText() {
        for (String query : List.of(
                "name=%3Cscript%3Ealert(1)%3C%2Fscript%3E",
                "name=a%0Ab",
                "name=",
                "name",
                "name=%zz",
                "name=a%00b")) {
            App.Response response = app.handle("GET", "/hello", query);
            assertEquals(400, response.status(), query);
            assertEquals("invalid name\n", response.body(), query);
        }
    }

    @Test
    void helloAcceptsTheLongestNameAndRefusesOneCharacterMore() {
        String longest = "a".repeat(App.MAX_NAME_LENGTH);
        assertEquals(200, app.handle("GET", "/hello", "name=" + longest).status());
        assertEquals(400, app.handle("GET", "/hello", "name=" + longest + "a").status());
    }

    @Test
    void versionIsReportedAsJson() {
        App.Response response = app.handle("GET", "/version", null);
        assertEquals("{\"version\":\"1.2.3\"}\n", response.body());
        assertEquals("application/json", response.headers().get("Content-Type"));
    }

    @Test
    void aMissingVersionIsDevAndAnOddOneIsUnknown() {
        assertEquals("{\"version\":\"dev\"}\n", new App(null).handle("GET", "/version", null).body());
        assertEquals("{\"version\":\"unknown\"}\n", new App("1\"},{\"x").handle("GET", "/version", null).body());
    }

    @Test
    void anUnknownPathIsNotFound() {
        assertEquals(404, app.handle("GET", "/admin", null).status());
        assertEquals(404, app.handle("GET", "/hello/", null).status());
    }

    @Test
    void onlyGetAndHeadAreAllowed() {
        App.Response response = app.handle("POST", "/hello", null);
        assertEquals(405, response.status());
        assertEquals("GET, HEAD", response.headers().get("Allow"));
        assertEquals(200, app.handle("HEAD", "/healthz", null).status());
    }

    @Test
    void everyResponseCarriesTheDefensiveHeaders() {
        List<App.Response> responses = List.of(
                app.handle("GET", "/healthz", null),
                app.handle("GET", "/hello", "name=x"),
                app.handle("GET", "/hello", "name="),
                app.handle("GET", "/version", null),
                app.handle("GET", "/nope", null),
                app.handle("DELETE", "/healthz", null),
                App.internalError());
        for (App.Response response : responses) {
            for (String header : DEFENSIVE_HEADERS) {
                assertTrue(response.headers().containsKey(header), response.status() + " lacks " + header);
            }
        }
    }

    @Test
    void anInternalErrorSaysNothingAboutTheCause() {
        App.Response response = App.internalError();
        assertEquals(500, response.status());
        assertEquals("internal error\n", response.body());
    }

    @Test
    void queryParameterFindsTheFirstMatchAndNothingElse() {
        assertEquals(Optional.of("b"), App.queryParameter("a=1&k=b&k=c", "k"));
        assertEquals(Optional.empty(), App.queryParameter("a=1", "k"));
        assertEquals(Optional.empty(), App.queryParameter("", "k"));
        assertEquals(Optional.empty(), App.queryParameter(null, "k"));
        assertEquals(Optional.of(""), App.queryParameter("k", "k"));
    }
}
