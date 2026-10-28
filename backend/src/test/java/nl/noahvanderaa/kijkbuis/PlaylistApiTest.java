package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistApiTest {

    private static final String PLAYLIST = """
            #EXTM3U url-tvg="http://epg.example/guide.xml"
            #EXTINF:-1 tvg-id="one.nl" tvg-logo="http://logos.example/one.png" group-title="NL" tvg-chno="1",Channel One
            http://streams.example/one
            #EXTINF:-1 tvg-id="two.nl" group-title="NL",Channel Two
            http://streams.example/two
            """;

    @TempDir
    static Path tempDir;
    private static TestServer server;
    private static MockPlaylistServer playlists;
    private static String admin;
    private static String viewer;

    @BeforeAll
    static void startServers() throws Exception {
        server = TestServer.start(tempDir);
        playlists = new MockPlaylistServer();
        admin = server.login("admin-1", "admin", List.of("admins"));
        viewer = server.login("viewer-1", "viewer", List.of());
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
        if (playlists != null) {
            playlists.close();
        }
    }

    @Test
    void adminAddsPlaylistAndViewersSeeItsChannels() throws Exception {
        playlists.serve("/basic.m3u", 200, PLAYLIST);
        HttpResponse<String> created = createPlaylist("Provider", playlists.url("/basic.m3u"));
        assertEquals(201, created.statusCode(), created.body());
        JsonObject playlist = json(created).getAsJsonObject();
        assertEquals(2, playlist.get("channelCount").getAsInt());
        assertTrue(playlist.get("lastRefreshError").isJsonNull());
        assertEquals("http://epg.example/guide.xml", playlist.get("detectedEpgUrl").getAsString());
        assertEquals(720, playlist.get("refreshIntervalMinutes").getAsInt());

        List<JsonObject> channels = channels(viewer, null);
        assertEquals(2, channels.size());
        JsonObject one = channels.getFirst();
        assertEquals("Channel One", one.get("name").getAsString());
        assertEquals("NL", one.get("group").getAsString());
        assertEquals(1, one.get("channelNumber").getAsInt());
        assertEquals("one.nl", one.get("tvgId").getAsString());
        assertEquals("http://logos.example/one.png", one.get("logoUrl").getAsString());
        assertEquals(playlist.get("id"), one.get("playlistId"));
        assertFalse(one.has("streamUrl"), "Stream URLs contain provider credentials, viewers must not see them");
        assertEquals("Channel Two", channels.get(1).get("name").getAsString());

        HttpResponse<String> oneForAdmin = server.get("/api/channels/" + one.get("id").getAsString(), admin);
        assertEquals("http://streams.example/one", json(oneForAdmin).getAsJsonObject().get("streamUrl").getAsString());

        List<JsonElement> viewerPlaylists = json(server.get("/api/playlists", viewer)).getAsJsonArray().asList();
        assertEquals(1, viewerPlaylists.size());
        JsonObject viewerPlaylist = viewerPlaylists.getFirst().getAsJsonObject();
        assertEquals("Provider", viewerPlaylist.get("name").getAsString());
        assertFalse(viewerPlaylist.has("url"), "Playlist URLs contain provider credentials, viewers must not see them");
    }

    @Test
    void viewersCannotManagePlaylists() throws Exception {
        playlists.serve("/viewers.m3u", 200, PLAYLIST);
        String id = createdId(createPlaylist("Provider", playlists.url("/viewers.m3u")));

        assertEquals(403, server.send("POST", "/api/playlists", viewer, playlistJson("Mine", playlists.url("/viewers.m3u"))).statusCode());
        assertEquals(403, server.send("PATCH", "/api/playlists/" + id, viewer, "{\"name\": \"Mine\"}").statusCode());
        assertEquals(403, server.send("POST", "/api/playlists/" + id + "/refresh", viewer, null).statusCode());
        assertEquals(403, server.send("DELETE", "/api/playlists/" + id, viewer, null).statusCode());
        assertEquals(401, server.get("/api/channels", null).statusCode());
        assertEquals(401, server.get("/api/playlists", null).statusCode());
    }

    @Test
    void refreshKeepsChannelIdsStable() throws Exception {
        playlists.serve("/stable.m3u", 200, """
                #EXTM3U
                #EXTINF:-1 tvg-id="one.nl",Channel One
                http://streams.example/one
                #EXTINF:-1 tvg-id="two.nl",Channel Two
                http://streams.example/two
                #EXTINF:-1,No EPG
                http://streams.example/no-epg
                #EXTINF:-1 tvg-id="shared.nl",Shared HD
                http://streams.example/shared-hd
                #EXTINF:-1 tvg-id="shared.nl",Shared SD
                http://streams.example/shared-sd
                """);
        String id = createdId(createPlaylist("Provider", playlists.url("/stable.m3u")));
        Map<String, String> before = channelIdsByName(id);
        assertEquals(5, before.size());

        playlists.serve("/stable.m3u", 200, """
                #EXTM3U
                #EXTINF:-1 tvg-id="shared.nl",Shared SD
                http://streams.example/shared-sd
                #EXTINF:-1,No EPG (renamed)
                http://streams.example/no-epg
                #EXTINF:-1 tvg-id="one.nl",Channel One HD
                http://streams.example/one-hd
                #EXTINF:-1 tvg-id="shared.nl",Shared HD
                http://streams.example/shared-hd
                #EXTINF:-1 tvg-id="three.nl",Channel Three
                http://streams.example/three
                """);
        assertEquals(5, refresh(id).get("channelCount").getAsInt());

        List<JsonObject> channels = channels(admin, id);
        assertEquals(List.of("Shared SD", "No EPG (renamed)", "Channel One HD", "Shared HD", "Channel Three"),
                channels.stream().map((channel) -> channel.get("name").getAsString()).toList());
        assertEquals("http://streams.example/one-hd", channels.get(2).get("streamUrl").getAsString());

        Map<String, String> after = channelIdsByName(id);
        assertEquals(before.get("Channel One"), after.get("Channel One HD"), "Same tvg-id, so the same channel");
        assertEquals(before.get("No EPG"), after.get("No EPG (renamed)"), "No tvg-id but the same URL, so the same channel");
        assertEquals(before.get("Shared HD"), after.get("Shared HD"), "Shared tvg-id, so matched by URL");
        assertEquals(before.get("Shared SD"), after.get("Shared SD"), "Shared tvg-id, so matched by URL");
        assertFalse(before.containsValue(after.get("Channel Three")));
        assertFalse(after.containsValue(before.get("Channel Two")));
    }

    @Test
    void refreshOnlyWritesChangedChannels() throws Exception {
        playlists.serve("/writes.m3u", 200, PLAYLIST);
        String id = createdId(createPlaylist("Provider", playlists.url("/writes.m3u")));

        // Record which channels get updated
        try (Connection connection = server.app().database().connection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE channel_updates (name TEXT)");
            statement.execute("CREATE TRIGGER record_channel_updates AFTER UPDATE ON channels BEGIN INSERT INTO channel_updates VALUES (NEW.name); END");
        }
        try {
            refresh(id);
            assertEquals(List.of(), channelUpdates(), "Nothing changed, so nothing should be written");

            playlists.serve("/writes.m3u", 200, PLAYLIST.replace("Channel Two", "Channel 2"));
            refresh(id);
            assertEquals(List.of("Channel 2"), channelUpdates());
        } finally {
            try (Connection connection = server.app().database().connection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER record_channel_updates");
                statement.execute("DROP TABLE channel_updates");
            }
        }
    }

    @Test
    void failedRefreshKeepsChannels() throws Exception {
        playlists.serve("/flaky.m3u", 200, PLAYLIST);
        String id = createdId(createPlaylist("Provider", playlists.url("/flaky.m3u")));

        playlists.serve("/flaky.m3u", 500, "Oops");
        JsonObject failed = refresh(id);
        assertEquals("The playlist URL responded with HTTP 500", failed.get("lastRefreshError").getAsString());
        assertEquals(2, failed.get("channelCount").getAsInt());

        playlists.serve("/flaky.m3u", 200, "#EXTM3U\n");
        assertEquals("The playlist contains no channels", refresh(id).get("lastRefreshError").getAsString());
        assertEquals(2, channels(viewer, id).size());

        playlists.serve("/flaky.m3u", 200, PLAYLIST);
        assertTrue(refresh(id).get("lastRefreshError").isJsonNull());
    }

    @Test
    void playlistIsAddedEvenIfItCannotBeLoaded() throws Exception {
        HttpResponse<String> created = createPlaylist("Broken", playlists.url("/missing.m3u"));
        assertEquals(201, created.statusCode(), created.body());
        JsonObject playlist = json(created).getAsJsonObject();
        assertEquals(0, playlist.get("channelCount").getAsInt());
        assertEquals("The playlist URL responded with HTTP 404", playlist.get("lastRefreshError").getAsString());
    }

    @Test
    void updatesAndDeletesPlaylists() throws Exception {
        playlists.serve("/update.m3u", 200, PLAYLIST);
        String id = createdId(createPlaylist("Provider", playlists.url("/update.m3u")));

        JsonObject updated = patch(id, "{\"name\": \"Renamed\", \"epgUrl\": \"http://epg.example/other.xml\", \"refreshIntervalMinutes\": 60}");
        assertEquals("Renamed", updated.get("name").getAsString());
        assertEquals("http://epg.example/other.xml", updated.get("epgUrl").getAsString());
        assertEquals(60, updated.get("refreshIntervalMinutes").getAsInt());
        assertEquals(playlists.url("/update.m3u"), updated.get("url").getAsString());

        JsonObject cleared = patch(id, "{\"epgUrl\": null}");
        assertTrue(cleared.get("epgUrl").isJsonNull());
        assertEquals("Renamed", cleared.get("name").getAsString());

        // Changing the URL reloads the channels right away
        playlists.serve("/other.m3u", 200, "#EXTM3U\n#EXTINF:-1,Only\nhttp://streams.example/only\n");
        assertEquals(1, patch(id, "{\"url\": \"" + playlists.url("/other.m3u") + "\"}").get("channelCount").getAsInt());

        assertEquals(204, server.send("DELETE", "/api/playlists/" + id, admin, null).statusCode());
        assertEquals(404, server.get("/api/playlists/" + id, admin).statusCode());
        assertEquals(0, channels(admin, id).size());
        assertEquals(404, server.send("DELETE", "/api/playlists/" + id, admin, null).statusCode());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        String url = playlists.url("/valid.m3u");
        assertEquals("name is required", error(server.send("POST", "/api/playlists", admin, "{\"url\": \"" + url + "\"}"), 400));
        assertEquals("url is required", error(server.send("POST", "/api/playlists", admin, "{\"name\": \"A\"}"), 400));
        assertEquals("url must be an http:// or https:// URL", error(server.send("POST", "/api/playlists", admin, playlistJson("A", "ftp://x.example/a.m3u")), 400));
        assertEquals(400, server.send("POST", "/api/playlists", admin, playlistJson(" ", url)).statusCode());
        assertEquals(400, server.send("POST", "/api/playlists", admin, "{\"name\": \"A\", \"url\": \"" + url + "\", \"refreshIntervalMinutes\": 1}").statusCode());
        assertEquals(400, server.send("POST", "/api/playlists", admin, "{\"name\": \"A\", \"url\": \"" + url + "\", \"refreshIntervalMinutes\": 60.5}").statusCode());
        assertEquals(400, server.send("POST", "/api/playlists", admin, "{\"name\": 5, \"url\": \"" + url + "\"}").statusCode());
        assertEquals("The request body must be a JSON object", error(server.send("POST", "/api/playlists", admin, "not json"), 400));

        assertEquals(404, server.send("PATCH", "/api/playlists/" + UUID.randomUUID(), admin, "{\"name\": \"A\"}").statusCode());
        assertEquals(404, server.get("/api/playlists/not-a-uuid", admin).statusCode());
        assertEquals(404, server.get("/api/channels/" + UUID.randomUUID(), viewer).statusCode());
        assertEquals(400, server.get("/api/channels?playlist=not-a-uuid", viewer).statusCode());
        assertTrue(json(server.get("/api/playlists", admin)).getAsJsonArray().isEmpty(), "Invalid requests must not add playlists");
    }

    @NotNull
    private static HttpResponse<String> createPlaylist(@NotNull String name, @NotNull String url) throws Exception {
        return server.send("POST", "/api/playlists", admin, playlistJson(name, url));
    }

    @NotNull
    private static String playlistJson(@NotNull String name, @NotNull String url) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("url", url);
        return body.toString();
    }

    @NotNull
    private static String createdId(@NotNull HttpResponse<String> response) {
        assertEquals(201, response.statusCode(), response.body());
        return json(response).getAsJsonObject().get("id").getAsString();
    }

    @NotNull
    private static JsonObject refresh(@NotNull String id) throws Exception {
        HttpResponse<String> response = server.send("POST", "/api/playlists/" + id + "/refresh", admin, null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).getAsJsonObject();
    }

    @NotNull
    private static JsonObject patch(@NotNull String id, @NotNull String body) throws Exception {
        HttpResponse<String> response = server.send("PATCH", "/api/playlists/" + id, admin, body);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).getAsJsonObject();
    }

    @NotNull
    private static List<JsonObject> channels(@NotNull String session, @Nullable String playlistId) throws Exception {
        HttpResponse<String> response = server.get("/api/channels" + (playlistId != null ? "?playlist=" + playlistId : ""), session);
        assertEquals(200, response.statusCode(), response.body());
        List<JsonObject> channels = new ArrayList<>();
        json(response).getAsJsonArray().forEach((channel) -> channels.add(channel.getAsJsonObject()));
        return channels;
    }

    @NotNull
    private static Map<String, String> channelIdsByName(@NotNull String playlistId) throws Exception {
        Map<String, String> ids = new HashMap<>();
        for (JsonObject channel : channels(admin, playlistId)) {
            String previous = ids.put(channel.get("name").getAsString(), channel.get("id").getAsString());
            assertNull(previous, "Channel names in these tests must be unique");
        }
        return ids;
    }

    @NotNull
    private static List<String> channelUpdates() throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = server.app().database().connection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT name FROM channel_updates")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        }
        return names;
    }

    @NotNull
    private static String error(@NotNull HttpResponse<String> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return json(response).getAsJsonObject().get("error").getAsString();
    }

}
