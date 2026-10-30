package nl.noahvanderaa.kijkbuis.http.handler.channel;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.channel.LogoCache;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Serves a channel's logo through Kijkbuis, since browsers can't always reach the host the playlist points at.
 */
public class GetChannelLogoHandler extends AuthenticatedHandler {

    public GetChannelLogoHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        Channel channel = this.app.channelManager().findVisibleTo(user, Requests.pathUuid(exchange, "channel", "Channel not found"));
        if (channel == null || channel.logoUrl() == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Channel not found");
        }

        LogoCache.Logo logo = this.app.logoCache().get(channel.logoUrl());
        if (logo == null) {
            throw new ApiException(StatusCodes.BAD_GATEWAY, "The channel's logo couldn't be loaded");
        }

        // The URL changes when the logo does, see Channel#logoPath
        Responses.image(exchange, logo.contentType(), logo.data());
    }

}
