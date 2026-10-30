package nl.noahvanderaa.kijkbuis.http.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for handlers that need a logged-in user.
 * Requests without a valid session are rejected with 401, and requests from users without the required privilege
 * with 403, before the handler is called.
 */
public abstract class AuthenticatedHandler implements HttpHandler {

    protected final KijkbuisApp app;
    private final Privilege requiredPrivilege;

    public AuthenticatedHandler(@NotNull KijkbuisApp app) {
        this(app, null);
    }

    /**
     * @param app               the app
     * @param requiredPrivilege the privilege a user needs to use this handler, or null if any logged-in user may
     */
    public AuthenticatedHandler(@NotNull KijkbuisApp app, @Nullable Privilege requiredPrivilege) {
        this.app = app;
        this.requiredPrivilege = requiredPrivilege;
    }

    @Override
    public final void handleRequest(HttpServerExchange exchange) throws Exception {
        User user = this.app.sessionManager().resolve(AuthCookies.read(exchange, AuthCookies.SESSION));
        if (user == null) {
            throw new ApiException(StatusCodes.UNAUTHORIZED, "You are not logged in");
        }
        if (this.requiredPrivilege != null && !user.hasPrivilege(this.requiredPrivilege)) {
            throw new ApiException(StatusCodes.FORBIDDEN, "You are not allowed to do this");
        }

        this.handleRequest(user, exchange);
    }

    public abstract void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception;

}
