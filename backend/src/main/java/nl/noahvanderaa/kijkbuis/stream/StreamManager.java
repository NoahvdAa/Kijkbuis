package nl.noahvanderaa.kijkbuis.stream;

import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class StreamManager {

    public static final String AUTO_QUALITY = "auto";
    private static final Logger LOGGER = LogManager.getLogger(StreamManager.class);
    /**
     * Stream URLs come from playlists, which come from third parties. Only allow network protocols, so a playlist
     * can't make ffmpeg read local files with file:// URLs.
     */
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https", "rtmp", "rtmps", "rtsp", "rtp", "udp", "srt");
    /**
     * How long a player is told its stream was stopped by an admin, rather than that it doesn't exist
     */
    private static final Duration KICKED_MEMORY = Duration.ofMinutes(10);

    private final KijkbuisConfig.StreamingConfig config;
    private final KijkbuisConfig.TranscodingConfig transcodingConfig;
    private final IngestServer ingestServer = new IngestServer(this);
    private final Map<UUID, Stream> streams = new ConcurrentHashMap<>();
    private final Map<String, Stream> streamsByPlaybackToken = new ConcurrentHashMap<>();
    /**
     * The playback tokens of streams an admin stopped, and when. Players would otherwise take the stream disappearing
     * for a hiccup, and start a new one.
     */
    private final Map<String, Instant> kickedPlaybackTokens = new ConcurrentHashMap<>();
    /**
     * What each channel's video was like when it was last watched, so the next stream can start with the right
     * settings.
     */
    private final Map<UUID, SourceVideo> sourceVideos = new ConcurrentHashMap<>();

    public StreamManager(@NotNull KijkbuisConfig.StreamingConfig config, @NotNull KijkbuisConfig.TranscodingConfig transcodingConfig) {
        this.config = config;
        this.transcodingConfig = transcodingConfig;
    }

    public void initialize() {
        this.ingestServer.start();
    }

    /**
     * Starts streaming a channel for a user, and waits until the first segment is available.
     * <p>
     * The video is passed through if the user's browser can play it, and transcoded otherwise (if the user may).
     * Whether the browser can play it depends on the channel's codec and whether it's interlaced, which are only known
     * for sure once ffmpeg has looked at the channel. So ffmpeg starts with what the channel was like last time, and is
     * restarted with different settings if that turns out to be wrong, before it produced anything.
     *
     * @param sessionId      the session starting it, so it's stopped when that session logs out
     * @param profile        the quality the user picked, or null to pick automatically
     * @param playableCodecs the video codecs the user's browser can play
     */
    @NotNull
    public Stream start(@NotNull User user, @NotNull String sessionId, @NotNull Channel channel,
                        @Nullable KijkbuisConfig.TranscodeProfile profile, @NotNull Set<String> playableCodecs) throws InterruptedException {
        if (!SUPPORTED_SCHEMES.contains(scheme(channel.streamUrl()))) {
            throw new ApiException(StatusCodes.BAD_GATEWAY, "This channel's stream URL is not supported");
        }
        boolean mayTranscode = user.hasPrivilege(Privilege.TRANSCODE);
        if (profile != null && !mayTranscode) {
            throw new ApiException(StatusCodes.FORBIDDEN, "You are not allowed to change the quality");
        }

        Instant deadline = Instant.now().plus(this.config.startupTimeout());
        SourceVideo knownSource = this.sourceVideos.get(channel.id());
        EncodingPlan plan = EncodingPlan.decide(profile, knownSource, playableCodecs, mayTranscode);
        if (plan == null) {
            throw unplayable(knownSource);
        }

        Stream stream = this.launch(user, sessionId, channel, profile, plan);
        try {
            SourceVideo source = stream.awaitSourceVideo(remaining(deadline));
            if (source != null) {
                this.sourceVideos.put(channel.id(), source);
            } else {
                this.sourceVideos.remove(channel.id());
            }

            EncodingPlan actualPlan = EncodingPlan.decide(profile, source, playableCodecs, mayTranscode);
            if (actualPlan == null) {
                this.stop(stream.id());
                throw unplayable(source);
            }
            if (!actualPlan.equals(plan)) {
                LOGGER.info("{} uses {} video, restarting its stream for {} with different settings", channel.name(),
                        source != null ? source.describe() : "no", user.username());
                this.stop(stream.id());
                stream = this.launch(user, sessionId, channel, profile, actualPlan);
            }

            stream.awaitReady(remaining(deadline));
        } catch (IOException ex) {
            this.stop(stream.id());
            LOGGER.warn("Failed to start {} for {}: {}", channel.name(), user.username(), ex.getMessage());
            throw new ApiException(StatusCodes.BAD_GATEWAY, "The channel could not be started");
        } catch (TimeoutException ex) {
            this.stop(stream.id());
            LOGGER.warn("{} took too long to start for {}", channel.name(), user.username());
            throw new ApiException(StatusCodes.GATEWAY_TIME_OUT, "The channel took too long to start");
        } catch (InterruptedException ex) {
            this.stop(stream.id());
            throw ex;
        }

        LOGGER.info("{} started watching {}, {}", user.username(), channel.name(), stream.plan().describe());
        return stream;
    }

    @Nullable
    public Stream find(@NotNull UUID id) {
        return this.streams.get(id);
    }

    /**
     * @return the stream, or null if it doesn't exist or belongs to someone else
     */
    @Nullable
    public Stream findOwnedBy(@NotNull User user, @NotNull UUID id) {
        Stream stream = this.streams.get(id);
        return stream != null && stream.owner().id().equals(user.id()) ? stream : null;
    }

    /**
     * @return every running stream, the longest running first
     */
    @NotNull
    public List<Stream> all() {
        return this.streams.values().stream().sorted(Comparator.comparing(Stream::startedAt)).toList();
    }

    /**
     * Stops every stream a user is watching, such as when they're signed out. Their players are told an admin stopped
     * them.
     */
    public void kickAllOwnedBy(@NotNull UUID userId) {
        for (Stream stream : this.streams.values()) {
            if (stream.owner().id().equals(userId)) {
                this.kick(stream.id());
            }
        }
    }

    /**
     * Stops the streams a session started, when it logs out. The user's streams on other devices keep going.
     */
    public void stopAllOfSession(@NotNull String sessionId) {
        for (Stream stream : this.streams.values()) {
            if (stream.sessionId().equals(sessionId)) {
                this.stop(stream.id());
            }
        }
    }

    /**
     * Stops someone's stream on behalf of an admin. Its player is told so, and shouldn't start a new one.
     *
     * @return whether the stream existed
     */
    public boolean kick(@NotNull UUID id) {
        Stream stream = this.streams.get(id);
        if (stream == null) {
            return false;
        }
        this.kickedPlaybackTokens.put(stream.playbackToken(), Instant.now());
        return this.stop(id);
    }

    /**
     * @return whether an admin stopped the stream with this playback token recently
     */
    public boolean wasKicked(@NotNull String playbackToken) {
        return this.kickedPlaybackTokens.containsKey(playbackToken);
    }

    /**
     * Finds the stream a playback URL belongs to. Knowing the token is all it takes to watch the stream.
     *
     * @return the stream, or null if there is no stream with this token
     */
    @Nullable
    public Stream findByPlaybackToken(@NotNull String token) {
        return this.streamsByPlaybackToken.get(token);
    }

    /**
     * Finds the stream ffmpeg is pushing files for.
     *
     * @return the stream, or null if it doesn't exist or the token is wrong
     */
    @Nullable
    Stream findForIngest(@NotNull String id, @NotNull String token) {
        Stream stream;
        try {
            stream = this.streams.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        if (stream == null || !MessageDigest.isEqual(stream.ingestToken().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return stream;
    }

    /**
     * @return whether the stream existed
     */
    public boolean stop(@NotNull UUID id) {
        Stream stream = this.streams.remove(id);
        if (stream == null) {
            return false;
        }
        this.streamsByPlaybackToken.remove(stream.playbackToken());
        stream.stop();
        return true;
    }

    /**
     * Stops streams nobody requested anything from for a while, such as when the viewer closed their browser.
     */
    public void stopIdle() {
        for (Stream stream : this.streams.values()) {
            if (stream.isIdle(this.config.idleTimeout())) {
                LOGGER.info("Stopping idle stream of {}", stream.channel().name());
                this.stop(stream.id());
            }
        }
        Instant forgetBefore = Instant.now().minus(KICKED_MEMORY);
        this.kickedPlaybackTokens.values().removeIf((kickedAt) -> kickedAt.isBefore(forgetBefore));
    }

    public void close() {
        for (UUID id : this.streams.keySet()) {
            this.stop(id);
        }
        this.ingestServer.stop();
    }

    /**
     * Creates a stream and starts its ffmpeg.
     */
    @NotNull
    private Stream launch(@NotNull User user, @NotNull String sessionId, @NotNull Channel channel,
                          @Nullable KijkbuisConfig.TranscodeProfile profile, @NotNull EncodingPlan plan) {
        Stream stream = new Stream(user, sessionId, channel, profile != null ? profile.id() : AUTO_QUALITY, plan);
        this.streams.put(stream.id(), stream);
        this.streamsByPlaybackToken.put(stream.playbackToken(), stream);
        try {
            stream.start(FfmpegCommand.build(this.config.ffmpegPath(), this.transcodingConfig.preset(), this.transcodingConfig.deinterlaceDoubleRate(),
                    channel.streamUrl(), this.ingestServer.ingestUri(stream), plan));
        } catch (IOException ex) {
            this.stop(stream.id());
            LOGGER.error("Failed to start ffmpeg, check streaming.ffmpeg-path in the config", ex);
            throw new ApiException(StatusCodes.INTERNAL_SERVER_ERROR, "Streaming is not available right now");
        }
        return stream;
    }

    @NotNull
    private static ApiException unplayable(@Nullable SourceVideo source) {
        return new ApiException(StatusCodes.UNPROCESSABLE_ENTITY, "Your browser can't play this channel's video ("
                + (source != null ? source.codec() : null) + "), and you're not allowed to transcode it");
    }

    @NotNull
    private static Duration remaining(@NotNull Instant deadline) {
        return Duration.between(Instant.now(), deadline);
    }

    @Nullable
    private static String scheme(@NotNull String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return scheme != null ? scheme.toLowerCase(Locale.ROOT) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
