package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.noahvanderaa.kijkbuis.api.ui.Slot;
import nl.noahvanderaa.kijkbuis.plugin.LoadedPlugin;
import nl.noahvanderaa.kijkbuis.plugin.PluginManager;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the plugin in src/testPlugin like Kijkbuis loads any plugin, next to jars that aren't plugins it can load.
 */
class PluginTest {

    private static final Path TEST_PLUGIN = Path.of(System.getProperty("kijkbuis.test-plugin"));
    private static final String TEST_PLUGIN_MAIN = "nl.noahvanderaa.kijkbuis.testplugin.TestPlugin";
    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(ZoneOffset.UTC);

    @TempDir
    static Path tempDir;
    private static MockPlaylistServer upstream;
    private static TestServer server;
    private static String viewer;
    private static String admin;
    private static final Map<String, String> channelIds = new HashMap<>();

    @BeforeAll
    static void startServers() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        Files.copy(TEST_PLUGIN, plugins.resolve("test-plugin.jar"));
        // Kijkbuis skips all of these, but still starts
        Files.copy(TEST_PLUGIN, plugins.resolve("test-plugin-again.jar"));
        writeJar(plugins.resolve("no-info.jar"), null);
        writeJar(plugins.resolve("too-new.jar"), info("too_new", TEST_PLUGIN_MAIN, "99.0"));
        writeJar(plugins.resolve("missing-main.jar"), info("missing_main", "nl.example.Missing", "0.0"));
        writeJar(plugins.resolve("bad-id.jar"), info("Bad ID", TEST_PLUGIN_MAIN, "0.0"));
        Files.writeString(plugins.resolve("not-a-jar.jar"), "Not a jar");
        copyTestPlugin(plugins.resolve("broken-migration.jar"), "broken_migration", Map.of(
                "migrations/2026-10-28_13-00_Broken.sql", "CREATE TABLE half_done (id TEXT); INSERT INTO nowhere VALUES (1);"));

        upstream = new MockPlaylistServer();
        upstream.serve("/channels.m3u", 200, """
                #EXTM3U
                #EXTINF:-1 tvg-id="live",Live
                %s
                #EXTINF:-1 tvg-id="blocked",Blocked
                %s
                """.formatted(upstream.url("/live.ts"), upstream.url("/blocked.ts")));
        Instant now = Instant.now();
        upstream.serve("/epg.xml", 200, """
                <tv>
                <programme channel="live" start="%1$s" stop="%2$s"><title>Live show</title></programme>
                <programme channel="blocked" start="%1$s" stop="%2$s"><title>Blocked show</title></programme>
                <programme channel="blocked" start="%2$s" stop="%3$s"><title>Blocked later</title></programme>
                </tv>
                """.formatted(XMLTV_TIME.format(now.minus(Duration.ofHours(1))), XMLTV_TIME.format(now.plus(Duration.ofHours(1))),
                XMLTV_TIME.format(now.plus(Duration.ofHours(2)))));

        server = TestServer.start(tempDir);
        admin = server.login("admin-1", "admin", List.of("admins"));
        viewer = server.login("viewer-1", "viewer", List.of());

        JsonObject body = new JsonObject();
        body.addProperty("name", "Plugin channels");
        body.addProperty("url", upstream.url("/channels.m3u"));
        body.addProperty("epgUrl", upstream.url("/epg.xml"));
        assertEquals(201, server.send("POST", "/api/playlists", admin, body.toString()).statusCode());
        awaitEpg();
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
    void onlyEnablesPluginsItCanLoad() {
        assertEquals(List.of("test"), server.app().pluginManager().plugins().stream().map((plugin) -> plugin.info().id()).toList());
    }

    @Test
    void adminsSeeTheEnabledPlugins() throws Exception {
        assertEquals(403, server.get("/api/plugins", viewer).statusCode());

        JsonArray plugins = json(server.get("/api/plugins", admin)).getAsJsonArray();
        assertEquals(1, plugins.size());
        JsonObject plugin = plugins.get(0).getAsJsonObject();
        String iconUrl = plugin.get("iconUrl").getAsString();
        assertTrue(iconUrl.startsWith("/api/plugins/test/icon?v="), iconUrl);
        plugin.remove("iconUrl");
        plugin.remove("pages");
        assertEquals(JsonParser.parseString("""
                {"id": "test", "name": "Test plugin", "version": "1.0.0", "apiVersion": "0.0",
                    "description": "Blocks the channel named Blocked, and writes down what it sees."}
                """), plugin);

        HttpResponse<String> icon = server.get(iconUrl, viewer);
        assertEquals(200, icon.statusCode());
        assertEquals("image/svg+xml", icon.headers().firstValue("content-type").orElseThrow());
        assertTrue(icon.headers().firstValue("content-security-policy").orElseThrow().contains("sandbox"), "SVGs can't run scripts");
        assertEquals("nosniff", icon.headers().firstValue("x-content-type-options").orElseThrow());
        assertTrue(icon.body().startsWith("<svg"));
        assertEquals(404, server.get("/api/plugins/nobody/icon", viewer).statusCode());
    }

    @Test
    void pluginsAddPagesForAdmins() throws Exception {
        JsonArray pages = json(server.get("/api/plugins", admin)).getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("pages");
        assertEquals(JsonParser.parseString("""
                [{"id": "lookup", "title": "Lookup", "subPages": [{"id": "details", "title": "Details"}]},
                    {"id": "broken", "title": "Broken", "subPages": []}]
                """), pages);

        // Opened for the first time, so the plugin picks the defaults
        JsonArray components = lookup("").getAsJsonArray("components");
        assertEquals(JsonParser.parseString("""
                {"type": "filter", "submitLabel": "Show", "inputs": [
                    {"type": "number", "name": "days", "label": "Look back", "help": null, "value": 7, "min": 1, "max": 365, "unit": "days",
                        "required": false},
                    {"type": "text", "name": "who", "label": "User", "help": null, "value": "", "placeholder": "Anyone", "required": false,
                        "maxLength": null, "multiline": false},
                    {"type": "checkbox", "name": "onlyStops", "label": "Only stopped streams", "help": null, "value": false},
                    {"type": "dateRange", "name": "period", "label": "Between", "help": null, "value": null, "required": false}
                ]}
                """), components.get(0));
        assertEquals("Looking back 7 days", components.get(1).getAsJsonObject().get("text").getAsString());
        assertEquals("Any day in " + ZoneId.systemDefault(), components.get(4).getAsJsonObject().get("text").getAsString(),
                "Without a time zone from the browser, it's the server's");
        assertEquals(JsonParser.parseString("""
                {"type": "table", "columns": ["Who", "Days", "When", "Note"], "emptyText": null,
                    "rows": [["Anyone", 7, {"time": "2026-10-30T12:00:00Z"}, null]]}
                """), components.get(3), "Text and numbers as themselves, and times so they can be told apart from text");

        // What's filled in comes in the query, where everything is text
        components = lookup("?days=30&who=viewer&onlyStops=true").getAsJsonArray("components");
        assertEquals("Looking back 30 days for viewer", components.get(1).getAsJsonObject().get("text").getAsString());
        assertEquals(List.of("30", "\"viewer\"", "true", "null"), components.get(0).getAsJsonObject().getAsJsonArray("inputs").asList().stream()
                .map((input) -> input.getAsJsonObject().get("value").toString())
                .toList());
        assertEquals("Looking back 7 days", lookup("?days=lots").getAsJsonArray("components").get(1).getAsJsonObject().get("text").getAsString(),
                "Numbers that aren't count as not filled in");

        // Date ranges are days in the browser's time zone, which it sends along
        components = lookup("?period=2026-10-01..2026-10-07", Map.of("X-Time-Zone", "Europe/Amsterdam")).getAsJsonArray("components");
        assertEquals("Between 2026-09-30T22:00:00Z and 2026-10-07T22:00:00Z in Europe/Amsterdam", components.get(4).getAsJsonObject().get("text").getAsString());
        assertEquals(JsonParser.parseString("{\"from\": \"2026-10-01\", \"to\": \"2026-10-07\"}"),
                components.get(0).getAsJsonObject().getAsJsonArray("inputs").get(3).getAsJsonObject().get("value"));
        assertEquals("Any day in Europe/Amsterdam", lookup("?period=2026-10-07..2026-10-01", Map.of("X-Time-Zone", "Europe/Amsterdam"))
                .getAsJsonArray("components").get(4).getAsJsonObject().get("text").getAsString(), "Ranges that end before they start count as not filled in");
        assertEquals("Between 2026-10-01T00:00:00Z and 2026-10-02T00:00:00Z in " + ZoneId.of("UTC"), lookup("?period=2026-10-01..2026-10-01", Map.of("X-Time-Zone", "UTC"))
                .getAsJsonArray("components").get(4).getAsJsonObject().get("text").getAsString());
        assertEquals("Any day in " + ZoneId.systemDefault(), lookup("", Map.of("X-Time-Zone", "Mars/Olympus_Mons"))
                .getAsJsonArray("components").get(4).getAsJsonObject().get("text").getAsString(), "Unknown time zones fall back to the server's");

        assertEquals(403, server.get("/api/plugins/test/pages/lookup", viewer).statusCode());
        assertEquals(404, server.get("/api/plugins/test/pages/missing", admin).statusCode());
        assertEquals(404, server.get("/api/plugins/test/pages/details", admin).statusCode(), "Sub-pages are only below their page");
        assertEquals(404, server.get("/api/plugins/nobody/pages/lookup", admin).statusCode());
        HttpResponse<String> broken = server.get("/api/plugins/test/pages/broken", admin);
        assertEquals(500, broken.statusCode());
        assertEquals("Something went wrong while putting this page together", json(broken).getAsJsonObject().get("error").getAsString());
    }

    @Test
    void pagesCanHaveSubPages() throws Exception {
        HttpResponse<String> response = server.get("/api/plugins/test/pages/lookup/details", admin);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject page = json(response).getAsJsonObject();
        assertEquals("lookup", page.get("parent").getAsString());
        assertEquals("Details", page.get("title").getAsString());
        assertEquals("Only on a sub-page", page.getAsJsonArray("sections").get(0).getAsJsonObject().getAsJsonArray("components").get(0).getAsJsonObject()
                .get("text").getAsString());

        assertEquals(403, server.get("/api/plugins/test/pages/lookup/details", viewer).statusCode());
        assertEquals(404, server.get("/api/plugins/test/pages/lookup/missing", admin).statusCode());
        assertEquals(404, server.get("/api/plugins/test/pages/broken/details", admin).statusCode(), "Sub-pages belong to one page");
    }

    @Test
    void pluginsOnlySeeTheApiAndTheJdk() {
        assertEquals(List.of("enabled test", "sees Kijkbuis: false", "sees Gson: false"), seen(testPlugin()).subList(0, 3));
    }

    @Test
    void pluginsCanScheduleTasks() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!seen(testPlugin()).contains("ticked 3 times")) {
            assertTrue(Instant.now().isBefore(deadline), "The task didn't run 3 times");
            Thread.sleep(50);
        }
    }

    @Test
    void pluginsCanStopStreamsFromStarting() throws Exception {
        HttpResponse<String> response = server.send("POST", "/api/streams", viewer, startJson("Blocked"));
        assertEquals(403, response.statusCode());
        assertEquals("The test plugin blocks this channel", json(response).getAsJsonObject().get("error").getAsString());

        List<String> seen = seen(testPlugin());
        assertTrue(seen.contains("start viewer Blocked auto cancelled"), seen.toString());
        assertTrue(seen.contains("on Blocked: Blocked show"), "Plugins can look up what's on: " + seen);
        assertTrue(seen.contains("now on Blocked: Blocked show, next: Blocked later"), seen.toString());
        assertTrue(seen.contains("found viewer (Test user viewer) with [], and null for an unknown ID"), "Plugins can look up users: " + seen);
        assertTrue(seen.contains("everyone: admin, viewer"), "In order of their display names: " + seen);
        assertFalse(seen.stream().anyMatch((line) -> line.startsWith("started ") && line.endsWith(" Blocked")), "It never started");
    }

    @Test
    void pluginsHearWhyStreamsStopped() throws Exception {
        upstream.serveSample(tempDir, 12, "/live.ts", "testsrc2=size=320x240:rate=25", "-c:v", "libx264", "-preset", "ultrafast", "-g", "25",
                "-pix_fmt", "yuv420p", "-c:a", "aac");

        String stoppedByViewer = startStream(viewer);
        assertEquals(204, server.send("DELETE", "/api/streams/" + stoppedByViewer, viewer, null).statusCode());
        String stoppedByAdmin = startStream(viewer);
        assertEquals(204, server.send("DELETE", "/api/streams/" + stoppedByAdmin, admin, null).statusCode());

        List<String> seen = seen(testPlugin());
        assertTrue(seen.contains("start viewer Live auto"), seen.toString());
        assertStartedThenStopped(seen, stoppedByViewer, "VIEWER");
        assertStartedThenStopped(seen, stoppedByAdmin, "ADMIN");

        // The plugin writes down what was watched in its database, with the programmes from the guide
        try (Connection connection = testPlugin().database().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT reason, programmes FROM stops WHERE stream_id = ?")) {
            statement.setString(1, stoppedByViewer);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("VIEWER", resultSet.getString("reason"));
                assertEquals("Live show", resultSet.getString("programmes"));
            }
        }
    }

    @Test
    void pluginsAddSectionsToPages() throws Exception {
        JsonArray sections = json(server.get("/api/ui/account", viewer)).getAsJsonArray();
        assertEquals(1, sections.size(), "The provider that throws is left out");
        JsonObject section = sections.get(0).getAsJsonObject();
        assertEquals("test", section.get("plugin").getAsString());
        assertEquals("Test plugin", section.get("title").getAsString());

        JsonArray components = section.getAsJsonArray("components");
        assertEquals(JsonParser.parseString("""
                {"type": "text", "text": "Hello, viewer"}
                """), components.get(0));
        assertEquals(JsonParser.parseString("""
                {"type": "stats", "stats": [{"label": "Streams stopped", "value": "42"}, {"label": "Watched", "value": "12 h"}]}
                """), components.get(1));
        assertEquals(JsonParser.parseString("""
                {"type": "list", "emptyText": "Nothing here", "items": [
                    {"title": "Live show", "subtitle": "Live", "detail": "5 min", "time": "2026-10-28T20:00:00Z", "imageUrl": "https://example.com/live.png"},
                    {"title": "Sneaky", "subtitle": null, "detail": null, "time": null, "imageUrl": null}
                ]}
                """), components.get(2), "Only http(s) images get through");
        assertEquals(JsonParser.parseString("""
                {"type": "form", "action": "/api/plugins/test/settings", "submitLabel": "Save settings", "successText": "Saved", "inputs": [
                    {"type": "text", "name": "note", "label": "Note", "help": "Shown nowhere", "value": "Hello", "placeholder": "Anything",
                        "required": false, "maxLength": 100, "multiline": false},
                    {"type": "number", "name": "days", "label": "Keep history for", "help": null, "value": 90, "min": 1, "max": 365, "unit": "days",
                        "required": true},
                    {"type": "checkbox", "name": "enabled", "label": "Keep a history", "help": null, "value": true},
                    {"type": "select", "name": "quality", "label": "Quality", "help": null, "value": "auto",
                        "options": [{"value": "auto", "label": "Auto"}, {"value": "low", "label": "Low"}]}
                ]}
                """), components.get(3), "Forms go to the plugin's endpoint, and selects start on an option they have");
        assertEquals(4, components.size(), "Filters only work on the plugin's own pages");
    }

    @Test
    void onlyAdminsSeeTheSectionsForAdmins() throws Exception {
        assertEquals(403, server.get("/api/ui/admin-overview", viewer).statusCode());
        JsonArray sections = json(server.get("/api/ui/admin-overview", admin)).getAsJsonArray();
        assertEquals(1, sections.size());
        assertEquals("Test plugin for admins", sections.get(0).getAsJsonObject().get("title").getAsString());

        assertEquals(404, server.get("/api/ui/nowhere", viewer).statusCode());
        assertEquals(401, server.get("/api/ui/account", null).statusCode());
    }

    @Test
    void pluginsSaveTheirForms() throws Exception {
        // As the admin, so the viewer's form keeps the values pluginsAddSectionsToPages expects
        HttpResponse<String> saved = server.send("POST", "/api/plugins/test/settings", admin, """
                {"note": "Changed", "days": 30, "enabled": false, "quality": "low"}
                """);
        assertEquals(204, saved.statusCode(), saved.body());

        List<JsonElement> values = settingsForm(admin).getAsJsonArray("inputs").asList().stream()
                .map((input) -> input.getAsJsonObject().get("value"))
                .toList();
        assertEquals(JsonParser.parseString("[\"Changed\", 30, false, \"low\"]").getAsJsonArray().asList(), values,
                "The section shows what was saved");
    }

    @Test
    void pluginsSayWhatsWrongWithAForm() throws Exception {
        for (String days : List.of("1000", "\"30\"", "1.5", "null", "99999999999999999999")) {
            HttpResponse<String> refused = server.send("POST", "/api/plugins/test/settings", admin, "{\"days\": " + days + ", \"quality\": \"low\"}");
            assertEquals(400, refused.statusCode(), days);
            assertEquals("Keep history for must be between 1 and 365 days", json(refused).getAsJsonObject().get("error").getAsString(), days);
        }
        assertEquals(400, server.send("POST", "/api/plugins/test/settings", admin, "Not JSON").statusCode());
    }

    @Test
    void formsOnlyGoToActionsAPluginHandles() throws Exception {
        assertEquals(204, server.send("POST", "/api/plugins/test/history/clear", viewer, "{}").statusCode(), "Actions can have slashes");
        assertTrue(seen(testPlugin()).contains("cleared by viewer"));

        assertEquals(404, server.send("POST", "/api/plugins/test/history", viewer, "{}").statusCode());
        assertEquals(404, server.send("POST", "/api/plugins/test/", viewer, "{}").statusCode());
        assertEquals(404, server.send("POST", "/api/plugins/nobody/settings", viewer, "{}").statusCode());
        assertEquals(401, server.send("POST", "/api/plugins/test/settings", null, "{}").statusCode());

        HttpResponse<String> broken = server.send("POST", "/api/plugins/test/broken", viewer, "{}");
        assertEquals(500, broken.statusCode());
        assertEquals("Something went wrong while saving this", json(broken).getAsJsonObject().get("error").getAsString());
    }

    @Test
    void pluginsGetADatabaseOfTheirOwn() throws Exception {
        assertTrue(Files.isRegularFile(tempDir.resolve("plugins/test/test.db")));
        try (Connection connection = testPlugin().database().getConnection()) {
            assertTrue(tables(connection).contains("stops"));
        }
        try (Connection connection = server.app().database().connection()) {
            assertFalse(tables(connection).contains("stops"), "Kijkbuis' own database is left alone");
        }
    }

    @Test
    void failedMigrationsLeaveNothingBehind() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("plugins/broken_migration/broken_migration.db"))) {
            assertEquals(List.of(), tables(connection), "The migrations that did work are rolled back too");
        }
    }

    @Test
    void keepsPluginDataAcrossRestarts(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("plugins"));
        Files.copy(TEST_PLUGIN, dir.resolve("plugins/test-plugin.jar"));
        try (TestServer first = TestServer.start(dir);
             Connection connection = first.app().pluginManager().plugins().getFirst().database().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO stops (stream_id, reason, programmes) VALUES ('kept', 'VIEWER', '')");
        }

        // The migration doesn't run again, which would fail now that the table exists
        try (TestServer second = TestServer.start(dir);
             Connection connection = second.app().pluginManager().plugins().getFirst().database().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT stream_id FROM stops")) {
            assertTrue(resultSet.next());
            assertEquals("kept", resultSet.getString("stream_id"));
        }
    }

    @Test
    void disablesPluginsWhenKijkbuisStops(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("plugins"));
        Files.copy(TEST_PLUGIN, dir.resolve("plugins/test-plugin.jar"));
        LoadedPlugin plugin;
        TestServer other = TestServer.start(dir);
        try (other) {
            plugin = other.app().pluginManager().plugins().getFirst();
            assertFalse(seen(plugin).contains("disabled"));
        }
        assertEquals("disabled", seen(plugin).getLast(), "Tasks are cancelled first");
        assertFalse(seen(plugin).contains("waited an hour"));
        User someone = new User(UUID.randomUUID(), "someone", "Someone", Set.of(), Set.of());
        assertEquals(List.of(), other.app().uiRegistry().sections(Slot.ACCOUNT, someone), "Its sections are gone");
    }

    /**
     * Checks that the test plugin heard the stream started, and then that it stopped and why.
     */
    private static void assertStartedThenStopped(@NotNull List<String> seen, @NotNull String streamId, @NotNull String reason) {
        int started = seen.indexOf("started " + streamId + " Live");
        assertTrue(started >= 0, "Started: " + seen);
        assertTrue(seen.indexOf("stop " + streamId + " Live " + reason) > started, "Then stopped: " + seen);
    }

    /**
     * Waits until the EPG is imported, which happens in the background after adding the playlist.
     */
    private static void awaitEpg() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            boolean imported = true;
            for (JsonElement element : json(server.get("/api/channels", viewer)).getAsJsonArray()) {
                JsonObject channel = element.getAsJsonObject();
                channelIds.put(channel.get("name").getAsString(), channel.get("id").getAsString());
                imported &= !channel.get("now").isJsonNull();
            }
            if (imported) {
                return;
            }
            assertTrue(Instant.now().isBefore(deadline), "The EPG wasn't imported in time");
            Thread.sleep(100);
        }
    }

    /**
     * @return the stream's ID
     */
    @NotNull
    private static String startStream(@NotNull String session) throws Exception {
        HttpResponse<String> response = server.send("POST", "/api/streams", session, startJson("Live"));
        assertEquals(201, response.statusCode(), response.body());
        return json(response).getAsJsonObject().get("id").getAsString();
    }

    @NotNull
    private static String startJson(@NotNull String channelName) {
        JsonObject body = new JsonObject();
        body.addProperty("channelId", channelIds.get(channelName));
        return body.toString();
    }

    /**
     * @return the section on the test plugin's lookup page, as an admin sees it
     */
    @NotNull
    private static JsonObject lookup(@NotNull String query) throws Exception {
        return lookup(query, Map.of());
    }

    /**
     * @param headers what else the browser sends, such as its time zone
     */
    @NotNull
    private static JsonObject lookup(@NotNull String query, @NotNull Map<String, String> headers) throws Exception {
        HttpResponse<String> response = server.send("GET", "/api/plugins/test/pages/lookup" + query, admin, null, headers);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject page = json(response).getAsJsonObject();
        assertEquals("Lookup", page.get("title").getAsString());
        return page.getAsJsonArray("sections").get(0).getAsJsonObject();
    }

    /**
     * @return the test plugin's settings form, as the user sees it on their account page
     */
    @NotNull
    private static JsonObject settingsForm(@NotNull String session) throws Exception {
        JsonArray sections = json(server.get("/api/ui/account", session)).getAsJsonArray();
        return sections.get(0).getAsJsonObject().getAsJsonArray("components").get(3).getAsJsonObject();
    }

    @NotNull
    private static LoadedPlugin testPlugin() {
        return server.app().pluginManager().plugins().getFirst();
    }

    /**
     * @return what the test plugin saw so far
     */
    @SuppressWarnings("unchecked")
    @NotNull
    private static List<String> seen(@NotNull LoadedPlugin plugin) {
        return ((Supplier<List<String>>) plugin.instance()).get();
    }

    @NotNull
    private static List<String> tables(@NotNull Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            List<String> tables = new ArrayList<>();
            while (resultSet.next()) {
                tables.add(resultSet.getString("name"));
            }
            return tables;
        }
    }

    /**
     * @param info the kijkbuis-plugin.json to put in it, if any
     */
    private static void writeJar(@NotNull Path path, @Nullable String info) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry(info != null ? PluginManager.INFO_FILE : "README.txt"));
            jar.write((info != null ? info : "Not a plugin").getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Copies the test plugin under another ID, with extra files in its jar.
     */
    private static void copyTestPlugin(@NotNull Path path, @NotNull String id, @NotNull Map<String, String> extraFiles) throws IOException {
        try (JarFile source = new JarFile(TEST_PLUGIN.toFile());
             JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (JarEntry entry : Collections.list(source.entries())) {
                jar.putNextEntry(new JarEntry(entry.getName()));
                if (entry.getName().equals(PluginManager.INFO_FILE)) {
                    jar.write(info(id, TEST_PLUGIN_MAIN, "0.0").getBytes(StandardCharsets.UTF_8));
                } else {
                    try (InputStream in = source.getInputStream(entry)) {
                        in.transferTo(jar);
                    }
                }
            }
            for (Map.Entry<String, String> file : extraFiles.entrySet()) {
                jar.putNextEntry(new JarEntry(file.getKey()));
                jar.write(file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @NotNull
    private static String info(@NotNull String id, @NotNull String main, @NotNull String apiVersion) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", "Not loaded");
        json.addProperty("version", "1.0.0");
        json.addProperty("main", main);
        json.addProperty("apiVersion", apiVersion);
        return json.toString();
    }

}
