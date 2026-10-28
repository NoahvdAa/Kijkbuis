package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.playlist.Playlist;
import nl.noahvanderaa.kijkbuis.playlist.PlaylistChanges;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Adds a playlist and loads its channels before responding, so problems with the URL show up right away.
 */
public class CreatePlaylistHandler extends AuthenticatedHandler {

    public CreatePlaylistHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        PlaylistChanges fields = PlaylistRequests.parseChanges(Requests.jsonBody(exchange));
        if (fields.name() == null) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "name is required");
        }
        if (fields.url() == null) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "url is required");
        }

        Playlist playlist = this.app.playlistManager().create(
                fields.name(),
                fields.url(),
                fields.epgUrl(),
                fields.refreshInterval() != null ? fields.refreshInterval() : PlaylistRequests.DEFAULT_REFRESH_INTERVAL
        );
        Responses.json(exchange, StatusCodes.CREATED, playlist.serialize(user));
    }

}
