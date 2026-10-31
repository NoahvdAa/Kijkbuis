package nl.noahvanderaa.kijkbuis.http.handler.stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.auth.SessionManager;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.AuthCookies;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.stream.EncodingPlan;
import nl.noahvanderaa.kijkbuis.stream.Stream;
import nl.noahvanderaa.kijkbuis.stream.StreamManager;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Starts streaming a channel, and responds once the first segment is available so the player can start right away.
 * The request body contains:
 * <ul>
 *     <li>{@code channelId}: the channel to watch</li>
 *     <li>{@code quality}: optional, auto (the default) or the ID of a transcoding profile</li>
 *     <li>{@code videoCodecs}: optional, the video codecs the browser can play, such as ["h264", "hevc"]. Defaults
 *     to only H.264, which every browser can play.</li>
 * </ul>
 */
public class StartStreamHandler extends AuthenticatedHandler {

    private static final Set<String> DEFAULT_VIDEO_CODECS = Set.of("h264");

    public StartStreamHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        JsonObject body = Requests.jsonBody(exchange);

        UUID channelId;
        try {
            channelId = UUID.fromString(body.get("channelId").getAsString());
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException |
                 NullPointerException ex) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "channelId must be a channel ID");
        }

        KijkbuisConfig.TranscodeProfile profile = null;
        if (body.has("quality")) {
            JsonElement quality = body.get("quality");
            if (!quality.isJsonPrimitive() || !quality.getAsJsonPrimitive().isString()) {
                throw new ApiException(StatusCodes.BAD_REQUEST, "quality must be a string");
            }
            if (!quality.getAsString().equals(StreamManager.AUTO_QUALITY)) {
                profile = this.app.config().transcoding().profile(quality.getAsString());
                if (profile == null) {
                    throw new ApiException(StatusCodes.BAD_REQUEST, "Unknown quality " + quality.getAsString());
                }
            }
        }

        Set<String> videoCodecs = DEFAULT_VIDEO_CODECS;
        if (body.has("videoCodecs")) {
            if (!body.get("videoCodecs").isJsonArray()) {
                throw new ApiException(StatusCodes.BAD_REQUEST, "videoCodecs must be a list of codec names");
            }
            List<String> codecs = new ArrayList<>();
            for (JsonElement codec : body.getAsJsonArray("videoCodecs")) {
                if (!codec.isJsonPrimitive() || !codec.getAsJsonPrimitive().isString()) {
                    throw new ApiException(StatusCodes.BAD_REQUEST, "videoCodecs must be a list of codec names");
                }
                codecs.add(codec.getAsString());
            }
            videoCodecs = EncodingPlan.normalizeCodecs(codecs);
        }

        Channel channel = this.app.channelManager().findVisibleTo(user, channelId);
        if (channel == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Channel not found");
        }

        // The request is authenticated, so it has a session cookie
        String sessionId = SessionManager.idOf(Objects.requireNonNull(AuthCookies.read(exchange, AuthCookies.SESSION)));
        Stream stream = this.app.streamManager().start(user, sessionId, channel, profile, videoCodecs);
        Responses.json(exchange, StatusCodes.CREATED, stream.serialize(user));
    }

}
