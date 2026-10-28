package nl.noahvanderaa.kijkbuis.http.handler.channel;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.epg.EpgManager;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lists the channels the user can watch, with what's on now and next. The optional {@code playlist} query parameter
 * limits the list to one playlist.
 */
public class ListChannelsHandler extends AuthenticatedHandler {

    public ListChannelsHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        List<Channel> channels = this.app.channelManager().channelsVisibleTo(user, Requests.queryUuid(exchange, "playlist"));
        Map<UUID, EpgManager.NowNext> nowNext = this.app.epgManager().nowAndNext(channels);

        JsonArray response = new JsonArray();
        for (Channel channel : channels) {
            response.add(ChannelJson.withNowNext(channel, nowNext.get(channel.id()), user));
        }
        Responses.json(exchange, response);
    }

}
