package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpgApiTest {

    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(ZoneOffset.UTC);

    @TempDir
    static Path tempDir;
    private static TestServer server;
    private static MockPlaylistServer upstream;
    private static String admin;
    private static String viewer;
    private static Instant now;

    @BeforeAll
    static void startServers() throws Exception {
        server = TestServer.start(tempDir);
        upstream = new MockPlaylistServer();
        admin = server.login("admin-1", "admin", List.of("admins"));
        viewer = server.login("viewer-1", "viewer", List.of());

        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        upstream.serve("/epg.xml", 200, epg());
        upstream.serve("/epg.xml.gz", 200, gzip(epg()));
    }

    @AfterEach
    void removePlaylists() throws Exception {
        for (JsonElement playlist : json(server.get("/api/playlists", admin)).getAsJsonArray()) {
            server.send("DELETE", "/api/playlists/" + playlist.getAsJsonObject().get("id").getAsString(), admin, null);
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
    void channelsShowWhatIsOnNowAndNext() throws Exception {
        String playlistId = createPlaylist("/now-next.m3u", upstream.url("/epg.xml.gz"));
        JsonObject playlist = waitForEpg(playlistId);
        assertTrue(playlist.get("epgLastRefreshError").isJsonNull(), playlist.toString());
        assertFalse(playlist.get("epgLastRefreshedAt").isJsonNull());
        assertEquals(upstream.url("/epg.xml.gz"), playlist.get("activeEpgUrl").getAsString());

        Map<String, JsonObject> channels = channelsByName(viewer);
        assertEquals("A", title(channels.get("One"), "now"));
        assertEquals(now.minus(Duration.ofMinutes(30)).toString(), channels.get("One").getAsJsonObject("now").get("startsAt").getAsString());
        assertEquals("B", title(channels.get("One"), "next"));
        JsonObject next = channels.get("One").getAsJsonObject("next");
        assertEquals("https://images.example/b-poster.jpg", next.get("posterUrl").getAsString());
        assertEquals("https://images.example/b-backdrop.jpg", next.get("backdropUrl").getAsString());
        assertTrue(channels.get("One").getAsJsonObject("now").get("posterUrl").isJsonNull());
        assertEquals("A", title(channels.get("One HD"), "now"), "Channels sharing a tvg-id share the EPG");
        assertEquals(null, title(channels.get("Two"), "now"));
        assertEquals("Later", title(channels.get("Two"), "next"));
        assertEquals(null, title(channels.get("Unmapped"), "now"));
        assertEquals(null, title(channels.get("Unmapped"), "next"));

        JsonObject one = json(server.get("/api/channels/" + channels.get("One").get("id").getAsString(), viewer)).getAsJsonObject();
        assertEquals("A", title(one, "now"));
        assertEquals("Today's episode", one.getAsJsonObject("now").get("subtitle").getAsString());
    }

    @Test
    void guideListsProgrammesInWindow() throws Exception {
        String playlistId = createPlaylist("/guide.m3u", upstream.url("/epg.xml"));
        waitForEpg(playlistId);
        Map<String, JsonObject> channels = channelsByName(viewer);
        String one = channels.get("One").get("id").getAsString();

        JsonObject guide = guide("from=" + now + "&to=" + now.plus(Duration.ofHours(4)));
        List<JsonObject> oneProgrammes = programmes(guide, one);
        assertEquals(List.of("A", "B", "C", "D"), oneProgrammes.stream().map((programme) -> programme.get("title").getAsString()).toList());
        assertEquals(oneProgrammes.get(3).get("startsAt"), oneProgrammes.get(2).get("endsAt"), "Without a stop time, a programme ends when the next one starts");
        assertEquals(List.of("Later"), programmes(guide, channels.get("Two").get("id").getAsString()).stream().map((programme) -> programme.get("title").getAsString()).toList());
        assertFalse(guide.getAsJsonObject("channels").has(channels.get("Unmapped").get("id").getAsString()));

        JsonObject filtered = guide("channel=" + one);
        assertEquals(1, filtered.getAsJsonObject("channels").size());
        assertEquals("A", programmes(filtered, one).getFirst().get("title").getAsString());

        // Programmes that ended long ago aren't kept at all
        JsonObject past = guide("from=" + now.minus(Duration.ofDays(2)) + "&to=" + now.minus(Duration.ofHours(1)));
        assertEquals(0, past.getAsJsonObject("channels").size());
    }

    @Test
    void adminsCanMapChannelsToOtherEpgChannels() throws Exception {
        String playlistId = createPlaylist("/mapping.m3u", upstream.url("/epg.xml"));
        waitForEpg(playlistId);
        String unmapped = channelsByName(admin).get("Unmapped").get("id").getAsString();

        HttpResponse<String> epgChannels = server.get("/api/playlists/" + playlistId + "/epg-channels", admin);
        assertEquals(200, epgChannels.statusCode());
        assertEquals(List.of("Channel One", "Other Channel", "two.nl"), json(epgChannels).getAsJsonArray().asList().stream()
                .map((channel) -> channel.getAsJsonObject().get("displayName").isJsonNull()
                        ? channel.getAsJsonObject().get("id").getAsString()
                        : channel.getAsJsonObject().get("displayName").getAsString())
                .toList());
        assertEquals(403, server.get("/api/playlists/" + playlistId + "/epg-channels", viewer).statusCode());

        assertEquals(403, server.send("PATCH", "/api/channels/" + unmapped, viewer, "{\"epgChannelId\": \"other.nl\"}").statusCode());
        HttpResponse<String> mapped = server.send("PATCH", "/api/channels/" + unmapped, admin, "{\"epgChannelId\": \"other.nl\"}");
        assertEquals(200, mapped.statusCode(), mapped.body());
        assertEquals("other.nl", json(mapped).getAsJsonObject().get("epgChannelId").getAsString());
        waitForEpg(playlistId);
        assertEquals("Other show", title(channelsByName(viewer).get("Unmapped"), "now"));

        // The mapping survives refreshing the playlist
        assertEquals(200, server.send("POST", "/api/playlists/" + playlistId + "/refresh", admin, null).statusCode());
        waitForEpg(playlistId);
        assertEquals("Other show", title(channelsByName(viewer).get("Unmapped"), "now"));

        HttpResponse<String> reset = server.send("PATCH", "/api/channels/" + unmapped, admin, "{\"epgChannelId\": null}");
        assertEquals("nomatch.nl", json(reset).getAsJsonObject().get("epgChannelId").getAsString());
        waitForEpg(playlistId);
        assertEquals(null, title(channelsByName(viewer).get("Unmapped"), "now"));
    }

    @Test
    void failedEpgRefreshKeepsProgrammes() throws Exception {
        upstream.serve("/flaky-epg.xml", 200, epg());
        String playlistId = createPlaylist("/flaky.m3u", upstream.url("/flaky-epg.xml"));
        waitForEpg(playlistId);

        upstream.serve("/flaky-epg.xml", 500, "Oops");
        assertEquals("The EPG URL responded with HTTP 500", refreshAndWaitForEpg(playlistId).get("epgLastRefreshError").getAsString());
        assertEquals("A", title(channelsByName(viewer).get("One"), "now"));

        upstream.serve("/flaky-epg.xml", 200, "<html>Not an EPG");
        String error = refreshAndWaitForEpg(playlistId).get("epgLastRefreshError").getAsString();
        assertTrue(error.startsWith("The EPG is not valid XML"), error);
        assertEquals("A", title(channelsByName(viewer).get("One"), "now"));

        upstream.serve("/flaky-epg.xml", 200, epg());
        assertTrue(refreshAndWaitForEpg(playlistId).get("epgLastRefreshError").isJsonNull());
    }

    @Test
    void unchangedEpgIsOnlyImportedAgainWhenNeeded() throws Exception {
        upstream.serve("/cached-epg.xml", 200, epg());
        String playlistId = createPlaylist("/cached.m3u", upstream.url("/cached-epg.xml"));
        waitForEpg(playlistId);
        assertEquals(List.of(200), upstream.statuses("/cached-epg.xml"));

        // Scheduled refreshes only download the EPG if it changed
        server.app().epgManager().refresh(UUID.fromString(playlistId), false);
        JsonObject refreshed = waitForEpg(playlistId);
        assertEquals(List.of(200, 304), upstream.statuses("/cached-epg.xml"));
        assertTrue(refreshed.get("epgLastRefreshError").isJsonNull());
        assertEquals("A", title(channelsByName(viewer).get("One"), "now"));

        // An admin reloading the playlist wants fresh data, so the EPG is downloaded either way
        refreshAndWaitForEpg(playlistId);
        assertEquals(List.of(200, 304, 200), upstream.statuses("/cached-epg.xml"));

        // Mapping a channel to another EPG channel needs programmes that weren't imported, so the EPG is downloaded again
        String unmapped = channelsByName(admin).get("Unmapped").get("id").getAsString();
        assertEquals(200, server.send("PATCH", "/api/channels/" + unmapped, admin, "{\"epgChannelId\": \"other.nl\"}").statusCode());
        waitForEpg(playlistId);
        assertEquals(List.of(200, 304, 200, 200), upstream.statuses("/cached-epg.xml"));
        assertEquals("Other show", title(channelsByName(viewer).get("Unmapped"), "now"));
    }

    @Test
    void epgUrlCanBeSetAndCleared() throws Exception {
        String playlistId = createPlaylist("/no-epg.m3u", null);
        waitForEpg(playlistId);
        assertEquals(null, title(channelsByName(viewer).get("One"), "now"));

        server.send("PATCH", "/api/playlists/" + playlistId, admin, "{\"epgUrl\": \"" + upstream.url("/epg.xml") + "\"}");
        waitForEpg(playlistId);
        assertEquals("A", title(channelsByName(viewer).get("One"), "now"));

        server.send("PATCH", "/api/playlists/" + playlistId, admin, "{\"epgUrl\": null}");
        JsonObject cleared = waitForEpg(playlistId);
        assertTrue(cleared.get("epgLastRefreshedAt").isJsonNull());
        assertEquals(null, title(channelsByName(viewer).get("One"), "now"));
    }

    @Test
    void jsonResponsesAreGzippedWhenAccepted() throws Exception {
        HttpResponse<InputStream> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/guide"))
                .header("Cookie", viewer)
                .header("Accept-Encoding", "gzip")
                .build(), HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertEquals("gzip", response.headers().firstValue("content-encoding").orElse(null));
        try (InputStream body = new GZIPInputStream(response.body())) {
            assertTrue(JsonParser.parseString(new String(body.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().has("channels"));
        }
    }

    @Test
    void rejectsInvalidGuideWindows() throws Exception {
        assertEquals(400, server.get("/api/guide?from=" + now + "&to=" + now.minusSeconds(1), viewer).statusCode());
        assertEquals(400, server.get("/api/guide?from=" + now + "&to=" + now.plus(Duration.ofHours(49)), viewer).statusCode());
        assertEquals(400, server.get("/api/guide?from=yesterday", viewer).statusCode());
        assertEquals(400, server.get("/api/guide?channel=nope", viewer).statusCode());
        assertEquals(401, server.get("/api/guide", null).statusCode());
    }

    /**
     * Adds a playlist with the test channels.
     *
     * @param epgUrl the EPG URL the playlist advertises, if any
     * @return the playlist ID
     */
    @NotNull
    private static String createPlaylist(@NotNull String path, @Nullable String epgUrl) throws Exception {
        upstream.serve(path, 200, (epgUrl != null ? "#EXTM3U url-tvg=\"" + epgUrl + "\"\n" : "#EXTM3U\n") + """
                #EXTINF:-1 tvg-id="one.nl",One
                http://streams.example/one
                #EXTINF:-1 tvg-id="one.nl",One HD
                http://streams.example/one-hd
                #EXTINF:-1 tvg-id="two.nl",Two
                http://streams.example/two
                #EXTINF:-1 tvg-id="nomatch.nl",Unmapped
                http://streams.example/unmapped
                """);

        JsonObject body = new JsonObject();
        body.addProperty("name", "EPG test");
        body.addProperty("url", upstream.url(path));
        HttpResponse<String> response = server.send("POST", "/api/playlists", admin, body.toString());
        assertEquals(201, response.statusCode(), response.body());
        return json(response).getAsJsonObject().get("id").getAsString();
    }

    @NotNull
    private static JsonObject refreshAndWaitForEpg(@NotNull String playlistId) throws Exception {
        assertEquals(200, server.send("POST", "/api/playlists/" + playlistId + "/refresh", admin, null).statusCode());
        return waitForEpg(playlistId);
    }

    /**
     * Waits for the playlist's EPG refresh to finish.
     *
     * @return the playlist
     */
    @NotNull
    private static JsonObject waitForEpg(@NotNull String playlistId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            JsonObject playlist = json(server.get("/api/playlists/" + playlistId, admin)).getAsJsonObject();
            if (!playlist.get("epgRefreshing").getAsBoolean()) {
                return playlist;
            }
            assertTrue(Instant.now().isBefore(deadline), "The EPG refresh took too long");
            Thread.sleep(50);
        }
    }

    @NotNull
    private static Map<String, JsonObject> channelsByName(@NotNull String session) throws Exception {
        Map<String, JsonObject> channels = new HashMap<>();
        for (JsonElement channel : json(server.get("/api/channels", session)).getAsJsonArray()) {
            channels.put(channel.getAsJsonObject().get("name").getAsString(), channel.getAsJsonObject());
        }
        return channels;
    }

    @Nullable
    private static String title(@NotNull JsonObject channel, @NotNull String field) {
        JsonElement programme = channel.get(field);
        return programme.isJsonNull() ? null : programme.getAsJsonObject().get("title").getAsString();
    }

    @NotNull
    private static JsonObject guide(@NotNull String query) throws Exception {
        HttpResponse<String> response = server.get("/api/guide?" + query.replace("+", URLEncoder.encode("+", StandardCharsets.UTF_8)), viewer);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).getAsJsonObject();
    }

    @NotNull
    private static List<JsonObject> programmes(@NotNull JsonObject guide, @NotNull String channelId) {
        return guide.getAsJsonObject("channels").getAsJsonArray(channelId).asList().stream().map(JsonElement::getAsJsonObject).toList();
    }

    @NotNull
    private static String epg() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <tv>
                  <channel id="one.nl"><display-name>Channel One</display-name></channel>
                  <channel id="other.nl"><display-name>Other Channel</display-name></channel>
                  <channel id="two.nl"/>
                  %s
                </tv>
                """.formatted(String.join("\n",
                programme("one.nl", "Old", -24 * 60, -23 * 60),
                programme("one.nl", "A", -30, 30).replace("</title>", "</title><sub-title>Today's episode</sub-title>"),
                programme("one.nl", "B", 30, 90).replace("</title>", "</title><image type=\"poster\">https://images.example/b-poster.jpg</image>"
                        + "<image type=\"backdrop\">https://images.example/b-backdrop.jpg</image>"),
                programme("one.nl", "C", 90, null),
                programme("one.nl", "D", 150, 210),
                programme("two.nl", "Later", 120, 180),
                programme("other.nl", "Other show", -10, 50)
        ));
    }

    /**
     * @param startMinutes when the programme starts, in minutes from now
     * @param stopMinutes  when the programme ends, in minutes from now, or null to leave out the stop time
     */
    @NotNull
    private static String programme(@NotNull String channel, @NotNull String title, int startMinutes, @Nullable Integer stopMinutes) {
        String stop = stopMinutes != null ? " stop=\"" + XMLTV_TIME.format(now.plus(Duration.ofMinutes(stopMinutes))) + "\"" : "";
        return "<programme channel=\"%s\" start=\"%s\"%s><title>%s</title></programme>"
                .formatted(channel, XMLTV_TIME.format(now.plus(Duration.ofMinutes(startMinutes))), stop, title);
    }

    private static byte @NotNull [] gzip(@NotNull String text) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return compressed.toByteArray();
    }

}
