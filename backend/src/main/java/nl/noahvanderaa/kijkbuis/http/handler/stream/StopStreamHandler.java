package nl.noahvanderaa.kijkbuis.http.handler.stream;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.stream.Stream;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Stops a stream right away. Players should call this when they're closed, idle streams are only stopped after the
 * idle timeout. Admins can stop anyone's stream.
 */
public class StopStreamHandler extends AuthenticatedHandler {

    public StopStreamHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        UUID id = Requests.pathUuid(exchange, "stream", "Stream not found");
        Stream stream = user.hasPrivilege(Privilege.ADMIN) ? this.app.streamManager().find(id) : this.app.streamManager().findOwnedBy(user, id);
        if (stream == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Stream not found");
        }

        if (user.hasPrivilege(Privilege.ADMIN)) {
            // Could be from the admin dashboard, so the player is told not to start it again. An admin's own player
            // calls this too when it switches channels, but then it doesn't ask for the old stream anymore anyway.
            this.app.streamManager().kick(stream.id());
        } else {
            this.app.streamManager().stop(stream.id());
        }
        exchange.setStatusCode(StatusCodes.NO_CONTENT);
    }

}
