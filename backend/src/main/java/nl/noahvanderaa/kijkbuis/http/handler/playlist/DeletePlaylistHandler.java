package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

public class DeletePlaylistHandler extends AuthenticatedHandler {

    public DeletePlaylistHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        if (!this.app.playlistManager().delete(Requests.pathUuid(exchange, "playlist", "Playlist not found"))) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Playlist not found");
        }
        exchange.setStatusCode(StatusCodes.NO_CONTENT);
    }

}
