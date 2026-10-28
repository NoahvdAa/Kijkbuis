package nl.noahvanderaa.kijkbuis.http.handler.channel;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.channel.LogoCache;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Serves a channel's logo through Kijkbuis, since browsers can't always reach the host the playlist points at.
 */
public class GetChannelLogoHandler extends AuthenticatedHandler {

    /**
     * Logos can be SVGs, which could run scripts when opened directly rather than as an image. This stops that.
     */
    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

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

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, logo.contentType());
        // The URL changes when the logo does, see Channel#logoPath
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "private, max-age=86400, immutable");
        exchange.getResponseHeaders().put(HttpString.tryFromString("X-Content-Type-Options"), "nosniff");
        exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Security-Policy"), CONTENT_SECURITY_POLICY);
        exchange.getResponseSender().send(ByteBuffer.wrap(logo.data()));
    }

}
