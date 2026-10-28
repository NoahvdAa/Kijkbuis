package nl.noahvanderaa.kijkbuis.http.handler.channel;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GetChannelHandler extends AuthenticatedHandler {

    public GetChannelHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        Channel channel = this.app.channelManager().findVisibleTo(user, Requests.pathUuid(exchange, "channel", "Channel not found"));
        if (channel == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Channel not found");
        }

        Responses.json(exchange, ChannelJson.withNowNext(channel, this.app.epgManager().nowAndNext(List.of(channel)).get(channel.id()), user));
    }

}
