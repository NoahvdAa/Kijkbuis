package nl.noahvanderaa.kijkbuis.http.handler.channel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.epg.Programme;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Returns the programmes airing in a time window, by channel. Query parameters, all optional:
 * <ul>
 *     <li>{@code from} and {@code to}: the window, as timestamps such as 2026-10-26T20:00:00Z. Defaults to the
 *     next 6 hours, and may cover at most 48 hours.</li>
 *     <li>{@code playlist}: only include the channels of this playlist</li>
 *     <li>{@code channel}: only include these channels, may be given several times</li>
 * </ul>
 * Channels without programmes in the window are left out.
 */
public class GetGuideHandler extends AuthenticatedHandler {

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(6);
    private static final Duration MAX_WINDOW = Duration.ofHours(48);

    public GetGuideHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        Instant from = Requests.queryInstant(exchange, "from");
        if (from == null) {
            from = Instant.now();
        }
        Instant to = Requests.queryInstant(exchange, "to");
        if (to == null) {
            to = from.plus(DEFAULT_WINDOW);
        }
        if (!to.isAfter(from)) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "to must be after from");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "The guide can cover at most " + MAX_WINDOW.toHours() + " hours at once");
        }

        Set<UUID> channelFilter = new HashSet<>();
        for (String channelId : Requests.queryParams(exchange, "channel")) {
            channelFilter.add(Requests.parseUuid("channel", channelId));
        }
        List<Channel> channels = this.app.channelManager().channelsVisibleTo(user, Requests.queryUuid(exchange, "playlist")).stream()
                .filter((channel) -> channelFilter.isEmpty() || channelFilter.contains(channel.id()))
                .toList();
        Map<UUID, List<Programme>> programmes = this.app.epgManager().programmes(channels, from, to);

        JsonObject byChannel = new JsonObject();
        for (Channel channel : channels) {
            List<Programme> channelProgrammes = programmes.get(channel.id());
            if (channelProgrammes != null) {
                JsonArray array = new JsonArray();
                channelProgrammes.forEach((programme) -> array.add(programme.serialize(user)));
                byChannel.add(channel.id().toString(), array);
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("from", from.toString());
        response.addProperty("to", to.toString());
        response.add("channels", byChannel);
        Responses.json(exchange, response);
    }

}
