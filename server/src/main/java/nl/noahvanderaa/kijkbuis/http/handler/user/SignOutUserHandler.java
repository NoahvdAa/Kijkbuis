package nl.noahvanderaa.kijkbuis.http.handler.user;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Signs a user out everywhere and stops what they're watching. Their groups are read again when they log back in, so
 * this also applies changes to their groups right away.
 */
public class SignOutUserHandler extends AuthenticatedHandler {

    private static final Logger LOGGER = LogManager.getLogger(SignOutUserHandler.class);

    public SignOutUserHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        UUID userId = Requests.pathUuid(exchange, "user", "User not found");
        int sessions = this.app.sessionManager().deleteAllOf(userId);
        // Playback URLs don't need a session, so their streams would otherwise keep going
        this.app.streamManager().kickAllOwnedBy(userId);

        LOGGER.info("{} signed out user {} from {} sessions", user.username(), userId, sessions);
        exchange.setStatusCode(StatusCodes.NO_CONTENT);
    }

}
