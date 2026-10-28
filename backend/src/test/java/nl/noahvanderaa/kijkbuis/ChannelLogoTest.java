package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Channel logos served through Kijkbuis, for browsers that can't reach the logo's host.
 */
class ChannelLogoTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3, 4};

    @TempDir
    static Path tempDir;
    private static MockPlaylistServer upstream;
    private static TestServer server;
    private static String viewer;
    private static final Map<String, JsonObject> channels = new HashMap<>();

    @BeforeAll
    static void startServers() throws Exception {
        upstream = new MockPlaylistServer();
        upstream.serve("/logos/typed.png", 200, "image/png", PNG);
        upstream.serve("/logos/untyped", 200, null, PNG);
        upstream.serve("/logos/page", 200, "text/html", "<html>secret</html>".getBytes(StandardCharsets.UTF_8));
        upstream.serve("/channels.m3u", 200, """
                #EXTM3U
                #EXTINF:-1 tvg-logo="%s",Typed
                http://upstream.example/1.ts
                #EXTINF:-1 tvg-logo="%s",Untyped
                http://upstream.example/2.ts
                #EXTINF:-1 tvg-logo="%s",Page
                http://upstream.example/3.ts
                #EXTINF:-1 tvg-logo="%s",Missing
                http://upstream.example/4.ts
                #EXTINF:-1,No logo
                http://upstream.example/5.ts
                """.formatted(upstream.url("/logos/typed.png"), upstream.url("/logos/untyped"), upstream.url("/logos/page"),
                upstream.url("/logos/missing.png")));

        server = TestServer.start(tempDir);
        String admin = server.login("admin-1", "admin", List.of("admins"));
        viewer = server.login("viewer-1", "viewer", List.of());

        JsonObject body = new JsonObject();
        body.addProperty("name", "Logos");
        body.addProperty("url", upstream.url("/channels.m3u"));
        assertEquals(201, server.send("POST", "/api/playlists", admin, body.toString()).statusCode());
        for (JsonElement channel : json(server.get("/api/channels", viewer)).getAsJsonArray()) {
            channels.put(channel.getAsJsonObject().get("name").getAsString(), channel.getAsJsonObject());
        }
    }

    @AfterAll
    static void stopServers() {
        if (server != null) {
            server.close();
        }
        if (upstream != null) {
            upstream.close();
        }
    }

    @Test
    void channelsPointAtKijkbuisForTheirLogo() {
        String id = channels.get("Typed").get("id").getAsString();
        String logoUrl = channels.get("Typed").get("logoUrl").getAsString();
        assertTrue(logoUrl.startsWith("/api/channels/" + id + "/logo?v="), logoUrl);
        assertFalse(logoUrl.contains("localhost"), "The logo's own host is never handed to browsers");
        assertTrue(channels.get("No logo").get("logoUrl").isJsonNull());
    }

    @Test
    void servesLogosAndKeepsThem() throws Exception {
        HttpResponse<byte[]> logo = logo("Typed", viewer);
        assertEquals(200, logo.statusCode());
        assertArrayEquals(PNG, logo.body());
        assertEquals("image/png", logo.headers().firstValue("content-type").orElseThrow());
        assertEquals("private, max-age=31536000, immutable", logo.headers().firstValue("cache-control").orElseThrow());
        assertEquals("nosniff", logo.headers().firstValue("x-content-type-options").orElseThrow());
        assertTrue(logo.headers().firstValue("content-security-policy").orElseThrow().contains("sandbox"));

        assertEquals(200, logo("Typed", viewer).statusCode());
        assertEquals(1, upstream.statuses("/logos/typed.png").size(), "The logo is fetched once, then kept");
    }

    @Test
    void recognisesImagesWithoutContentType() throws Exception {
        HttpResponse<byte[]> logo = logo("Untyped", viewer);
        assertEquals(200, logo.statusCode());
        assertEquals("image/png", logo.headers().firstValue("content-type").orElseThrow());
    }

    @Test
    void onlyServesImages() throws Exception {
        HttpResponse<byte[]> page = logo("Page", viewer);
        assertEquals(502, page.statusCode());
        assertFalse(new String(page.body(), StandardCharsets.UTF_8).contains("secret"), "Whatever else a logo URL returns stays out");
        assertEquals(502, logo("Missing", viewer).statusCode());
    }

    @Test
    void requiresLogin() throws Exception {
        assertEquals(401, logo("Typed", null).statusCode());
    }

    @NotNull
    private static HttpResponse<byte[]> logo(@NotNull String channel, @Nullable String session) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.baseUrl() + channels.get(channel).get("logoUrl").getAsString()));
        if (session != null) {
            request.header("Cookie", session);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

}
