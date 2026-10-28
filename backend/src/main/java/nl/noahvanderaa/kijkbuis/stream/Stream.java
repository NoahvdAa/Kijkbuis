package nl.noahvanderaa.kijkbuis.stream;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One user watching one channel. An ffmpeg process converts the channel to HLS with fMP4 segments, and pushes the
 * files to the {@link IngestServer}, which hands them to this stream to keep in memory.
 * <p>
 * The player fetches the files with a secret playback token in the URL instead of a session. That keeps the database
 * out of the most frequent requests, and lets players that can't send cookies (such as VLC or a Chromecast) play the
 * URL too.
 */
public class Stream implements KBSerializable {

    public static final String PLAYLIST = "live.m3u8";
    private static final Logger LOGGER = LogManager.getLogger(Stream.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LOG_LINES = 20;
    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(5);

    private final UUID id = UUID.randomUUID();
    private final String playbackToken = randomToken();
    private final String ingestToken = randomToken();
    private final User owner;
    private final String sessionId;
    private final Instant startedAt = Instant.now();
    private final Channel channel;
    private final String quality;
    private final EncodingPlan plan;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final CompletableFuture<Optional<SourceVideo>> sourceVideo = new CompletableFuture<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final Deque<String> log = new ArrayDeque<>();
    private volatile long lastRequestedAt = System.currentTimeMillis();
    private volatile boolean stopped = false;
    private Process process = null;
    private Thread logReader = null;

    /**
     * @param owner     the user watching the stream
     * @param sessionId the session that started it, see {@link nl.noahvanderaa.kijkbuis.auth.SessionManager#idOf}
     * @param channel   the channel being watched
     * @param quality   the quality the user asked for, which is auto or a transcoding profile ID
     * @param plan      how the video is encoded
     */
    Stream(@NotNull User owner, @NotNull String sessionId, @NotNull Channel channel, @NotNull String quality, @NotNull EncodingPlan plan) {
        this.owner = owner;
        this.sessionId = sessionId;
        this.channel = channel;
        this.quality = quality;
        this.plan = plan;
    }

    /**
     * Starts ffmpeg.
     *
     * @param command the ffmpeg command, see {@link FfmpegCommand}
     */
    void start(@NotNull List<String> command) throws IOException {
        this.process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        this.process.getOutputStream().close();
        this.logReader = Thread.ofVirtual().name("ffmpeg-" + this.id).start(this::readLog);
        this.process.onExit().thenRun(this::exited);
    }

    /**
     * Waits until ffmpeg has looked at the channel and logged what its video is like. That happens before it produces
     * anything, so there's still time to restart it with different settings.
     *
     * @return the channel's video, or null if the channel has no video
     * @throws IOException      if ffmpeg exited before that
     * @throws TimeoutException if it took too long
     */
    @Nullable
    SourceVideo awaitSourceVideo(@NotNull Duration timeout) throws IOException, TimeoutException, InterruptedException {
        return await(this.sourceVideo, timeout).orElse(null);
    }

    /**
     * Waits until the first segment is available.
     *
     * @throws IOException      if ffmpeg exited before that
     * @throws TimeoutException if it took too long
     */
    void awaitReady(@NotNull Duration timeout) throws IOException, TimeoutException, InterruptedException {
        await(this.ready, timeout);
    }

    /**
     * Stores a file pushed by ffmpeg.
     */
    void putFile(@NotNull String name, byte @NotNull [] data) {
        if (this.stopped) {
            return;
        }
        this.files.put(name, data);

        // ffmpeg only lists segments in the playlist once they're fully uploaded
        if (name.equals(PLAYLIST) && new String(data, StandardCharsets.UTF_8).contains("#EXTINF") && this.ready.complete(null)) {
            // Give the viewer the full idle timeout to start playing
            this.lastRequestedAt = System.currentTimeMillis();
        }
    }

    void deleteFile(@NotNull String name) {
        this.files.remove(name);
    }

    /**
     * Gets a file for the viewer. This counts as activity, so the stream isn't stopped for being idle.
     *
     * @return the file's contents, or null if there is no such file (anymore)
     */
    public byte @Nullable [] file(@NotNull String name) {
        this.lastRequestedAt = System.currentTimeMillis();
        return this.files.get(name);
    }

    /**
     * @return whether nothing was requested from this stream for the given time. Streams that are still starting
     * are never idle, the startup timeout covers those.
     */
    boolean isIdle(@NotNull Duration timeout) {
        return this.ready.isDone() && System.currentTimeMillis() - this.lastRequestedAt > timeout.toMillis();
    }

    /**
     * Stops ffmpeg and discards the stream's files.
     */
    void stop() {
        this.stopped = true;
        this.files.clear();

        Process process = this.process;
        if (process != null && process.isAlive()) {
            process.destroy();
            CompletableFuture.delayedExecutor(KILL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
                if (process.isAlive()) {
                    LOGGER.warn("ffmpeg for {} did not stop in time, killing it", this.channel.name());
                    process.destroyForcibly();
                }
            });
        }
    }

    public boolean isRunning() {
        return this.process != null && this.process.isAlive();
    }

    @NotNull
    public UUID id() {
        return this.id;
    }

    @NotNull
    public User owner() {
        return this.owner;
    }

    /**
     * @return the session that started the stream
     */
    @NotNull
    String sessionId() {
        return this.sessionId;
    }

    @NotNull
    public Instant startedAt() {
        return this.startedAt;
    }

    @NotNull
    public Channel channel() {
        return this.channel;
    }

    @NotNull
    public EncodingPlan plan() {
        return this.plan;
    }

    @NotNull
    String playbackToken() {
        return this.playbackToken;
    }

    @NotNull
    String ingestToken() {
        return this.ingestToken;
    }

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = new JsonObject();

        obj.addProperty("id", this.id.toString());
        obj.addProperty("channelId", this.channel.id().toString());
        obj.addProperty("channelName", this.channel.name());
        obj.addProperty("channelLogoUrl", this.channel.logoPath());
        obj.addProperty("playlistUrl", "/live/" + this.playbackToken + "/" + PLAYLIST);
        obj.addProperty("quality", this.quality);
        obj.addProperty("transcoding", this.plan.transcode());
        SourceVideo source = this.sourceVideo.isDone() && !this.sourceVideo.isCompletedExceptionally()
                ? this.sourceVideo.join().orElse(null)
                : null;
        obj.addProperty("sourceVideoCodec", source != null ? source.codec() : null);
        obj.addProperty("sourceInterlaced", source != null && source.interlaced());
        // The size it's shown at, so non-square pixels are accounted for
        obj.addProperty("sourceWidth", source != null && source.width() > 0 ? source.width() : null);
        obj.addProperty("sourceHeight", source != null && source.height() > 0 ? source.height() : null);
        obj.addProperty("startedAt", this.startedAt.toString());
        obj.add("user", this.owner.serialize(user));

        return obj;
    }

    private void readLog() {
        try (BufferedReader reader = this.process.errorReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                // The stream URL usually contains provider credentials, keep those out of the logs
                line = line.replace(this.channel.streamUrl(), "<stream url>");
                LOGGER.debug("[ffmpeg {}] {}", this.id, line);
                this.detectSourceVideo(line);
                synchronized (this.log) {
                    if (this.log.size() == LOG_LINES) {
                        this.log.removeFirst();
                    }
                    this.log.addLast(line);
                }
            }
        } catch (IOException ignored) {
            // ffmpeg is gone
        }
    }

    private void detectSourceVideo(@NotNull String line) {
        if (this.sourceVideo.isDone()) {
            return;
        }

        // The first video stream ffmpeg lists is the one that's used, since the command maps 0:v:0
        SourceVideo source = SourceVideo.parse(line);
        if (source != null) {
            this.sourceVideo.complete(Optional.of(source));
        } else if (line.startsWith("Stream mapping:") || line.startsWith("Output #")) {
            // All input streams have been listed, and none were video
            this.sourceVideo.complete(Optional.empty());
        }
    }

    private void exited() {
        try {
            // Make sure we have ffmpeg's last words before reporting them
            this.logReader.join(Duration.ofSeconds(1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        int exitCode = this.process.exitValue();
        if (!this.sourceVideo.isDone()) {
            this.sourceVideo.completeExceptionally(new IOException("ffmpeg exited with code " + exitCode + " before it found the channel's codecs: " + this.logTail()));
        }
        if (!this.ready.isDone()) {
            this.ready.completeExceptionally(new IOException("ffmpeg exited with code " + exitCode + " before the stream started: " + this.logTail()));
        } else if (exitCode != 0 && !this.stopped) {
            LOGGER.warn("ffmpeg for {} exited with code {}: {}", this.channel.name(), exitCode, this.logTail());
        }
    }

    @NotNull
    private String logTail() {
        synchronized (this.log) {
            return this.log.isEmpty() ? "(no output)" : String.join(" | ", this.log);
        }
    }

    /**
     * @return a random 256-bit token, which is unguessable
     */
    @NotNull
    private static String randomToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static <T> T await(@NotNull CompletableFuture<T> future, @NotNull Duration timeout) throws IOException, TimeoutException, InterruptedException {
        try {
            return future.get(Math.max(0, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(ex.getCause());
        }
    }

}
