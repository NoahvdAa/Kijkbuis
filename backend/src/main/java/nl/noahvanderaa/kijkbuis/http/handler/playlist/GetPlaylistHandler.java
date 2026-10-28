package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.playlist.Playlist;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

public class GetPlaylistHandler extends AuthenticatedHandler {

    public GetPlaylistHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        Playlist playlist = this.app.playlistManager().findVisibleTo(user, Requests.pathUuid(exchange, "playlist", "Playlist not found"));
        if (playlist == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Playlist not found");
        }
        Responses.json(exchange, playlist.serialize(user));
    }

}
