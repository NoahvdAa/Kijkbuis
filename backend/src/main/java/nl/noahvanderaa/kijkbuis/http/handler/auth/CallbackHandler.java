package nl.noahvanderaa.kijkbuis.http.handler.auth;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.auth.OidcClient;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The identity provider sends users here after they logged in. Creates their session and sends them back to where
 * they started the login.
 */
public class CallbackHandler implements HttpHandler {

    private static final Logger LOGGER = LogManager.getLogger(CallbackHandler.class);
    private final KijkbuisApp app;

    public CallbackHandler(@NotNull KijkbuisApp app) {
        this.app = app;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        boolean secure = this.app.config().server().secureCookies();

        Map<String, List<String>> params = new HashMap<>();
        exchange.getQueryParameters().forEach((name, values) -> params.put(name, List.copyOf(values)));
        String browserState = AuthCookies.read(exchange, AuthCookies.LOGIN_STATE);
        // The state is single-use, so clear it even if the login fails
        AuthCookies.clearLoginState(exchange, secure);

        OidcClient.CompletedLogin login = this.app.oidcClient().completeLogin(params, browserState);
        User user = this.app.userManager().upsert(login.identity());
        String token = this.app.sessionManager().create(user);
        LOGGER.info("{} logged in", user.username());

        AuthCookies.setSession(exchange, token, this.app.sessionManager().lifetime(), secure);
        Responses.redirect(exchange, this.app.config().server().publicUrl() + login.returnTo());
    }

}
