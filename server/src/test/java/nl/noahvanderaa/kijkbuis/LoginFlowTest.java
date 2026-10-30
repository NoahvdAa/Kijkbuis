package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static nl.noahvanderaa.kijkbuis.TestServer.cookie;
import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static nl.noahvanderaa.kijkbuis.TestServer.location;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFlowTest {

    @TempDir
    static Path tempDir;
    private static TestServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = TestServer.start(tempDir);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void rejectsRequestsWithoutSession() throws Exception {
        HttpResponse<String> response = server.get("/api/me", null);
        assertEquals(401, response.statusCode());
        assertEquals("You are not logged in", json(response).getAsJsonObject().get("error").getAsString());

        assertEquals(401, server.get("/api/me", AuthCookies.SESSION + "=made-up-token").statusCode());
    }

    @Test
    void loginCreatesSessionAndLogoutEndsIt() throws Exception {
        server.idp().nextUser("user-1", "alice", List.of("transcoders", "other"));
        HttpResponse<String> callback = server.loginCallback("/guide?channel=1");
        assertEquals(server.baseUrl() + "/guide?channel=1", location(callback));
        String session = TestServer.sessionCookie(callback);

        JsonObject me = me(session);
        assertEquals("alice", me.get("username").getAsString());
        assertEquals("Test user alice", me.get("displayName").getAsString());
        assertEquals(array("transcode"), me.get("privileges"));
        assertEquals(2, me.getAsJsonArray("groups").size());

        HttpResponse<String> logout = server.send("POST", "/api/auth/logout", session, null);
        assertEquals(204, logout.statusCode());
        assertEquals("", cookie(logout, AuthCookies.SESSION));
        assertEquals(401, server.get("/api/me", session).statusCode());
    }

    @Test
    void sessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
        server.idp().nextUser("user-1", "alice", List.of());
        String setCookie = server.loginCallback("/").headers().allValues("set-cookie").stream()
                .filter((header) -> header.startsWith(AuthCookies.SESSION + "="))
                .findFirst().orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertFalse(setCookie.contains("Secure"), setCookie);
    }

    @Test
    void adminsGetEveryPrivilege() throws Exception {
        assertEquals(array("admin", "transcode"), me(server.login("user-2", "bob", List.of("admins"))).get("privileges"));
    }

    @Test
    void loggingInAgainUpdatesGroups() throws Exception {
        JsonObject first = me(server.login("user-3", "carol", List.of("transcoders")));
        assertEquals(array("transcode"), first.get("privileges"));

        JsonObject second = me(server.login("user-3", "carol", List.of()));
        assertEquals(new JsonArray(), second.get("privileges"));
        assertEquals(first.get("id"), second.get("id"));
    }

    @Test
    void callbackRequiresTheBrowserThatStartedTheLogin() throws Exception {
        HttpResponse<String> start = server.get("/api/auth/login", null);
        String loginState = cookie(start, AuthCookies.LOGIN_STATE);
        String callbackUrl = location(server.get(location(start), null));

        assertEquals(400, server.get(callbackUrl, null).statusCode());
        // The state is single-use, so it can't be retried with the right cookie either
        assertEquals(400, server.get(callbackUrl, AuthCookies.LOGIN_STATE + "=" + loginState).statusCode());
    }

    @Test
    void callbackCannotBeReplayed() throws Exception {
        HttpResponse<String> start = server.get("/api/auth/login", null);
        String stateCookie = AuthCookies.LOGIN_STATE + "=" + cookie(start, AuthCookies.LOGIN_STATE);
        String callbackUrl = location(server.get(location(start), null));

        assertEquals(302, server.get(callbackUrl, stateCookie).statusCode());
        assertEquals(400, server.get(callbackUrl, stateCookie).statusCode());
    }

    @Test
    void loginOnlyRedirectsWithinKijkbuis() throws Exception {
        server.idp().nextUser("user-1", "alice", List.of());
        assertEquals(server.baseUrl() + "/", location(server.loginCallback("//evil.example")));
        assertEquals(server.baseUrl() + "/", location(server.loginCallback("https://evil.example")));
        assertEquals(server.baseUrl() + "/", location(server.loginCallback("/\\evil.example")));
    }

    @Test
    void unknownRoutesReturnJsonErrors() throws Exception {
        HttpResponse<String> response = server.get("/api/does-not-exist", null);
        assertEquals(404, response.statusCode());
        assertEquals("Not found", json(response).getAsJsonObject().get("error").getAsString());
    }

    @Test
    void databaseIsConfigured() throws Exception {
        try (Connection connection = server.app().database().connection();
             Statement statement = connection.createStatement()) {
            assertEquals("1", pragma(statement, "foreign_keys"));
            assertEquals("wal", pragma(statement, "journal_mode"));
            assertEquals("1", pragma(statement, "synchronous"), "synchronous should be NORMAL");
        }
    }

    @NotNull
    private static String pragma(@NotNull Statement statement, @NotNull String name) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA " + name)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    @NotNull
    private static JsonObject me(@NotNull String sessionCookie) throws Exception {
        HttpResponse<String> response = server.get("/api/me", sessionCookie);
        assertEquals(200, response.statusCode(), response.body());
        return json(response).getAsJsonObject();
    }

    @NotNull
    private static JsonArray array(@NotNull String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    @Test
    void adminsListUsersAndSignThemOutEverywhere() throws Exception {
        String admin = server.login("user-admin", "root", List.of("admins"));
        String phone = server.login("user-dave", "dave", List.of("transcoders"));
        String laptop = server.login("user-dave", "dave", List.of("transcoders"));
        assertEquals(403, server.get("/api/users", phone).statusCode());

        String daveId = me(phone).get("id").getAsString();
        JsonObject dave = null;
        for (JsonElement user : json(server.get("/api/users", admin)).getAsJsonArray()) {
            if (user.getAsJsonObject().get("id").getAsString().equals(daveId)) {
                dave = user.getAsJsonObject();
            }
        }
        assertNotNull(dave);
        assertEquals(2, dave.get("sessionCount").getAsInt());
        assertEquals("transcoders", dave.getAsJsonArray("groups").get(0).getAsString());
        assertEquals("transcode", dave.getAsJsonArray("privileges").get(0).getAsString());
        assertTrue(dave.has("lastLoginAt"));

        assertEquals(403, server.send("DELETE", "/api/users/" + daveId + "/sessions", phone, null).statusCode());
        assertEquals(204, server.send("DELETE", "/api/users/" + daveId + "/sessions", admin, null).statusCode());
        assertEquals(401, server.get("/api/me", phone).statusCode());
        assertEquals(401, server.get("/api/me", laptop).statusCode());
        assertEquals(200, server.get("/api/me", admin).statusCode(), "Other users stay logged in");
    }

}
