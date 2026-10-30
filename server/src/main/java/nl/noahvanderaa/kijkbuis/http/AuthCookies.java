package nl.noahvanderaa.kijkbuis.http;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * The cookies used for logging in. Both are HttpOnly, and SameSite=Lax so they aren't sent along with cross-site
 * POST requests.
 */
public final class AuthCookies {

    /**
     * Holds the session token of a logged-in user.
     */
    public static final String SESSION = "kijkbuis_session";
    /**
     * Holds the state of a login in progress, binding it to the browser that started it.
     */
    public static final String LOGIN_STATE = "kijkbuis_login";

    private static final String SESSION_PATH = "/";
    private static final String LOGIN_STATE_PATH = "/api/auth";

    private AuthCookies() {
    }

    @Nullable
    public static String read(@NotNull HttpServerExchange exchange, @NotNull String name) {
        Cookie cookie = exchange.getRequestCookie(name);
        return cookie != null ? cookie.getValue() : null;
    }

    public static void setSession(@NotNull HttpServerExchange exchange, @NotNull String token, @NotNull Duration maxAge, boolean secure) {
        set(exchange, SESSION, token, SESSION_PATH, maxAge, secure);
    }

    public static void clearSession(@NotNull HttpServerExchange exchange, boolean secure) {
        set(exchange, SESSION, "", SESSION_PATH, Duration.ZERO, secure);
    }

    public static void setLoginState(@NotNull HttpServerExchange exchange, @NotNull String state, @NotNull Duration maxAge, boolean secure) {
        set(exchange, LOGIN_STATE, state, LOGIN_STATE_PATH, maxAge, secure);
    }

    public static void clearLoginState(@NotNull HttpServerExchange exchange, boolean secure) {
        set(exchange, LOGIN_STATE, "", LOGIN_STATE_PATH, Duration.ZERO, secure);
    }

    private static void set(@NotNull HttpServerExchange exchange, @NotNull String name, @NotNull String value, @NotNull String path, @NotNull Duration maxAge, boolean secure) {
        exchange.setResponseCookie(new CookieImpl(name, value)
                .setPath(path)
                .setMaxAge((int) maxAge.toSeconds())
                .setHttpOnly(true)
                .setSecure(secure)
                .setSameSiteMode("Lax"));
    }

}
