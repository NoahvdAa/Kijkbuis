package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.playlist.Playlist;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

public class ListPlaylistsHandler extends AuthenticatedHandler {

    public ListPlaylistsHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        JsonArray playlists = new JsonArray();
        for (Playlist playlist : this.app.playlistManager().visibleTo(user)) {
            playlists.add(playlist.serialize(user));
        }
        Responses.json(exchange, playlists);
    }

}
