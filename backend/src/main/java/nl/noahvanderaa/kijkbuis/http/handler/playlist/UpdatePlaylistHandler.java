package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.playlist.Playlist;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Changes the fields present in the request body. Set epgUrl to null to clear it.
 */
public class UpdatePlaylistHandler extends AuthenticatedHandler {

    public UpdatePlaylistHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        UUID id = Requests.pathUuid(exchange, "playlist", "Playlist not found");
        Playlist playlist = this.app.playlistManager().update(id, PlaylistRequests.parseChanges(Requests.jsonBody(exchange)));
        if (playlist == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Playlist not found");
        }
        Responses.json(exchange, playlist.serialize(user));
    }

}
