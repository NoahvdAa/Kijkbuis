package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.epg.EpgChannel;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Lists the channels in a playlist's EPG, for mapping channels whose tvg-id doesn't match.
 */
public class ListEpgChannelsHandler extends AuthenticatedHandler {

    public ListEpgChannelsHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        UUID playlistId = Requests.pathUuid(exchange, "playlist", "Playlist not found");
        if (this.app.playlistManager().find(playlistId) == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Playlist not found");
        }

        JsonArray channels = new JsonArray();
        for (EpgChannel channel : this.app.epgManager().epgChannels(playlistId)) {
            channels.add(channel.serialize(user));
        }
        Responses.json(exchange, channels);
    }

}
