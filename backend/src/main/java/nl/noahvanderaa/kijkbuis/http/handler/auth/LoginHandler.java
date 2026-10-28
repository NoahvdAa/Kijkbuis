package nl.noahvanderaa.kijkbuis.http.handler.auth;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.auth.OidcClient;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sends the user to the identity provider to log in.
 * The optional {@code redirect} query parameter is the app-relative path to return to afterwards.
 */
public class LoginHandler implements HttpHandler {

    private final KijkbuisApp app;

    public LoginHandler(@NotNull KijkbuisApp app) {
        this.app = app;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        String returnTo = sanitizeReturnTo(Requests.queryParam(exchange, "redirect"));

        OidcClient.LoginRequest login = this.app.oidcClient().startLogin(returnTo);
        AuthCookies.setLoginState(exchange, login.state(), OidcClient.LOGIN_TIMEOUT, this.app.config().server().secureCookies());
        Responses.redirect(exchange, login.authorizationUri().toString());
    }

    /**
     * Only allows paths within Kijkbuis, so the login can't be abused to redirect users to other sites.
     */
    @NotNull
    static String sanitizeReturnTo(@Nullable String redirect) {
        if (redirect == null || !redirect.startsWith("/") || redirect.startsWith("//")
                || redirect.chars().anyMatch((c) -> c <= 0x20 || c >= 0x7f || c == '\\')) {
            return "/";
        }
        return redirect;
    }

}
