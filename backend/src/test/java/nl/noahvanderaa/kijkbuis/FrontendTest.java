package nl.noahvanderaa.kijkbuis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kijkbuis serving the built frontend next to the API.
 */
class FrontendTest {

    private static final String INDEX = "<!doctype html><title>Kijkbuis</title>";

    @TempDir
    static Path tempDir;
    private static TestServer server;

    @BeforeAll
    static void startServer() throws Exception {
        Path frontend = Files.createDirectories(tempDir.resolve("frontend/assets"));
        Files.writeString(frontend.resolveSibling("index.html"), INDEX);
        Files.writeString(frontend.resolve("index-abc123.js"), "console.log('hi');");
        Files.writeString(frontend.resolveSibling("favicon.ico"), "icon");
        // Outside the frontend, so it must not be served
        Files.writeString(tempDir.resolve("secret.txt"), "secret");

        Path data = Files.createDirectories(tempDir.resolve("data"));
        server = TestServer.start(data, "frontend-dir = \"%s\"".formatted(frontend.getParent().toString().replace("\\", "\\\\")), "");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesIndexForPages() throws Exception {
        for (String path : new String[]{"/", "/guide", "/admin/playlists/123"}) {
            HttpResponse<String> page = server.get(path, null);
            assertEquals(200, page.statusCode(), path);
            assertEquals(INDEX, page.body(), path);
            assertEquals("no-cache", page.headers().firstValue("cache-control").orElseThrow(), path + " has to be checked for a new version");
        }
    }

    @Test
    void servesFiles() throws Exception {
        HttpResponse<String> asset = server.get("/assets/index-abc123.js", null);
        assertEquals(200, asset.statusCode());
        assertEquals("console.log('hi');", asset.body());
        assertTrue(asset.headers().firstValue("content-type").orElseThrow().contains("javascript"), asset.headers().toString());
        assertEquals("public, max-age=31536000, immutable", asset.headers().firstValue("cache-control").orElseThrow());

        HttpResponse<String> icon = server.get("/favicon.ico", null);
        assertEquals(200, icon.statusCode());
        assertEquals("no-cache", icon.headers().firstValue("cache-control").orElseThrow());
    }

    @Test
    void missingFilesAreNotFound() throws Exception {
        HttpResponse<String> asset = server.get("/assets/index-old.js", null);
        assertEquals(404, asset.statusCode(), "An old asset shouldn't get the page instead");
        assertFalse(asset.headers().firstValue("cache-control").orElse("").contains("immutable"), "A 404 must not be cached for good");
    }

    @Test
    void apiAndStreamsStayJson() throws Exception {
        HttpResponse<String> api = server.get("/api/nothing", null);
        assertEquals(404, api.statusCode());
        assertEquals("Not found", json(api).getAsJsonObject().get("error").getAsString());
        assertEquals(404, server.get("/live/token", null).statusCode());
        // Real API routes still work
        assertEquals(401, server.get("/api/me", null).statusCode());
    }

    @Test
    void doesNotServeFilesOutsideTheFrontend() throws Exception {
        for (String path : new String[]{"/../secret.txt", "/%2e%2e/secret.txt", "/assets/..%2f..%2fsecret.txt"}) {
            HttpResponse<String> response = server.get(path, null);
            assertFalse(response.body().contains("secret"), path);
        }
    }

}
