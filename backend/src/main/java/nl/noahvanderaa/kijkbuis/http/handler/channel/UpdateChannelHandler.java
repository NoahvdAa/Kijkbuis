package nl.noahvanderaa.kijkbuis.http.handler.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Maps a channel to a different EPG channel, for when its tvg-id doesn't match the EPG. Set epgChannelId to null to
 * use the tvg-id again.
 */
public class UpdateChannelHandler extends AuthenticatedHandler {

    private static final int MAX_EPG_CHANNEL_ID_LENGTH = 255;

    public UpdateChannelHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        JsonObject body = Requests.jsonBody(exchange);
        if (!body.has("epgChannelId")) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "epgChannelId is required");
        }
        JsonElement element = body.get("epgChannelId");
        String epgChannelId = null;
        if (!element.isJsonNull()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().length() > MAX_EPG_CHANNEL_ID_LENGTH) {
                throw new ApiException(StatusCodes.BAD_REQUEST, "epgChannelId must be a string or null");
            }
            epgChannelId = element.getAsString().isBlank() ? null : element.getAsString().strip();
        }

        Channel channel = this.app.channelManager().setEpgChannelOverride(Requests.pathUuid(exchange, "channel", "Channel not found"), epgChannelId);
        if (channel == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Channel not found");
        }

        // Programmes are only stored for EPG channels that are in use, so load the newly mapped one. The EPG's cache key
        // changed with the mapping, so it's downloaded even if it didn't change.
        this.app.epgManager().refresh(channel.playlistId(), false);
        Responses.json(exchange, channel.serialize(user));
    }

}
