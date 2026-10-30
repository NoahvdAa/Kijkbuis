package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs Kijkbuis against a {@link MockOidcProvider}, with helpers to log in and make requests like a browser would.
 * Members of the "admins" group are admins, members of "transcoders" may transcode.
 */
final class TestServer implements AutoCloseable {

    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private final MockOidcProvider idp;
    private final KijkbuisApp app;
    private final String baseUrl;

    private TestServer(@NotNull MockOidcProvider idp, @NotNull KijkbuisApp app, @NotNull String baseUrl) {
        this.idp = idp;
        this.app = app;
        this.baseUrl = baseUrl;
    }

    /**
     * @param dir the directory to keep the config and database in
     */
    @NotNull
    static TestServer start(@NotNull Path dir) throws Exception {
        return start(dir, "");
    }

    /**
     * @param dir         the directory to keep the config and database in
     * @param extraConfig TOML to add to the end of the config
     */
    @NotNull
    static TestServer start(@NotNull Path dir, @NotNull String extraConfig) throws Exception {
        return start(dir, "", extraConfig);
    }

    /**
     * @param dir          the directory to keep the config and database in
     * @param serverConfig TOML to add to the [server] section
     * @param extraConfig  TOML to add to the end of the config
     */
    @NotNull
    static TestServer start(@NotNull Path dir, @NotNull String serverConfig, @NotNull String extraConfig) throws Exception {
        MockOidcProvider idp = new MockOidcProvider(freePort());

        int port = freePort();
        String baseUrl = "http://localhost:" + port;
        Path config = dir.resolve("kijkbuis.toml");
        // The trailing slash in public-url is on purpose, Kijkbuis should strip it
        Files.writeString(config, """
                [server]
                host = "localhost"
                port = %d
                public-url = "%s/"
                %s

                [database]
                path = "%s"

                [auth]
                issuer = "%s"
                client-id = "%s"
                client-secret = "%s"

                [auth.privileges]
                admin = ["admins"]
                transcode = ["transcoders"]

                """.formatted(port, baseUrl, serverConfig, dir.resolve("kijkbuis.db"), idp.issuer(), MockOidcProvider.CLIENT_ID, MockOidcProvider.CLIENT_SECRET) + extraConfig);

        KijkbuisApp app = new KijkbuisApp();
        app.start(config);
        return new TestServer(idp, app, baseUrl);
    }

    @NotNull
    MockOidcProvider idp() {
        return this.idp;
    }

    @NotNull
    KijkbuisApp app() {
        return this.app;
    }

    @NotNull
    String baseUrl() {
        return this.baseUrl;
    }

    /**
     * Logs in as the given user.
     *
     * @return the session cookie, ready to be used as a Cookie header
     */
    @NotNull
    String login(@NotNull String subject, @NotNull String username, @NotNull List<String> groups) throws Exception {
        this.idp.nextUser(subject, username, groups);
        return sessionCookie(this.loginCallback("/"));
    }

    /**
     * Goes through the whole login flow like a browser would, and returns the response to the final callback.
     */
    @NotNull
    HttpResponse<String> loginCallback(@NotNull String redirect) throws Exception {
        HttpResponse<String> start = this.get("/api/auth/login?redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8), null);
        assertEquals(302, start.statusCode(), start.body());
        assertTrue(location(start).startsWith(this.idp.issuer() + "/authorize?"));
        String stateCookie = AuthCookies.LOGIN_STATE + "=" + cookie(start, AuthCookies.LOGIN_STATE);

        HttpResponse<String> authorize = this.get(location(start), null);
        assertEquals(302, authorize.statusCode());

        HttpResponse<String> callback = this.get(location(authorize), stateCookie);
        assertEquals(302, callback.statusCode(), callback.body());
        assertEquals("", cookie(callback, AuthCookies.LOGIN_STATE));
        return callback;
    }

    @NotNull
    HttpResponse<String> get(@NotNull String url, @Nullable String cookie) throws Exception {
        return this.send("GET", url, cookie, null);
    }

    @NotNull
    HttpResponse<String> send(@NotNull String method, @NotNull String url, @Nullable String cookie, @Nullable String jsonBody) throws Exception {
        return this.send(method, url, cookie, jsonBody, Map.of());
    }

    /**
     * @param url      an absolute URL, or a path on Kijkbuis
     * @param cookie   the Cookie header to send, if any
     * @param jsonBody the JSON request body to send, if any
     * @param headers  other headers to send
     */
    @NotNull
    HttpResponse<String> send(@NotNull String method, @NotNull String url, @Nullable String cookie, @Nullable String jsonBody,
                              @NotNull Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url.startsWith("http") ? url : this.baseUrl + url))
                .method(method, jsonBody != null ? HttpRequest.BodyPublishers.ofString(jsonBody) : HttpRequest.BodyPublishers.noBody());
        headers.forEach(request::header);
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() {
        this.app.stop();
        this.idp.close();
    }

    @NotNull
    static JsonElement json(@NotNull HttpResponse<String> response) {
        return JsonParser.parseString(response.body());
    }

    @NotNull
    static String sessionCookie(@NotNull HttpResponse<?> callback) {
        return AuthCookies.SESSION + "=" + cookie(callback, AuthCookies.SESSION);
    }

    @NotNull
    static String location(@NotNull HttpResponse<?> response) {
        return response.headers().firstValue("location").orElseThrow();
    }

    /**
     * @return the value the response sets the cookie to
     */
    @NotNull
    static String cookie(@NotNull HttpResponse<?> response, @NotNull String name) {
        String header = response.headers().allValues("set-cookie").stream()
                .filter((value) -> value.startsWith(name + "="))
                .findFirst().orElse(null);
        assertNotNull(header, "Response does not set cookie " + name);
        int end = header.indexOf(';');
        return header.substring(name.length() + 1, end == -1 ? header.length() : end);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
