package nl.noahvanderaa.kijkbuis.http.handler.auth;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.auth.SessionManager;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import org.jetbrains.annotations.NotNull;

/**
 * Ends the user's session, and stops the streams it started. This only logs them out of Kijkbuis, not out of the
 * identity provider.
 */
public class LogoutHandler implements HttpHandler {

    private final KijkbuisApp app;

    public LogoutHandler(@NotNull KijkbuisApp app) {
        this.app = app;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String token = AuthCookies.read(exchange, AuthCookies.SESSION);
        if (token != null) {
            this.app.sessionManager().delete(token);
            // Playback URLs don't need a session, so the streams would otherwise keep going. Other tabs that share the
            // session then fail to start a new one, and are sent to the login page.
            this.app.streamManager().stopAllOfSession(SessionManager.idOf(token));
        }

        AuthCookies.clearSession(exchange, this.app.config().server().secureCookies());
        exchange.setStatusCode(StatusCodes.NO_CONTENT);
    }

}
