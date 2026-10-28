package nl.noahvanderaa.kijkbuis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static nl.noahvanderaa.kijkbuis.TestServer.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Streams generated samples through a real ffmpeg. Skipped when ffmpeg isn't installed.
 */
class StreamingTest {

    private static final int SAMPLE_SECONDS = 12;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    static Path tempDir;
    private static MockPlaylistServer upstream;
    private static TestServer server;
    private static String viewer;
    private static String otherViewer;
    private static String transcoder;
    private static String admin;
    private static final Map<String, String> channelIds = new HashMap<>();

    @BeforeAll
    static void startServers() throws Exception {
        assumeTrue(succeeds("ffmpeg", "-version"), "ffmpeg is not installed");
        assumeTrue(succeeds("ffprobe", "-version"), "ffprobe is not installed");
        upstream = new MockPlaylistServer();

        // H.264 with AC-3 audio, which browsers can't play so Kijkbuis always converts it
        serveSample("/live.ts", "testsrc2=size=320x240:rate=25", "-c:v", "libx264", "-preset", "ultrafast", "-g", "25", "-pix_fmt", "yuv420p", "-c:a", "ac3");
        // MPEG-2 video, which browsers can't play at all. Served twice, so one channel is only watched by one test
        serveSample("/mpeg2.ts", "testsrc2=size=320x240:rate=25", "-c:v", "mpeg2video", "-c:a", "mp2");
        serveSample("/mpeg2-unwatched.ts", "testsrc2=size=320x240:rate=25", "-c:v", "mpeg2video", "-c:a", "mp2");
        // Interlaced PAL in 16:9 with non-square pixels, like many SD channels
        serveSample("/anamorphic.ts", "testsrc2=size=720x576:rate=25", "-vf", "setsar=64/45", "-c:v", "libx264", "-preset", "ultrafast", "-g", "25",
                "-pix_fmt", "yuv420p", "-flags", "+ildct+ilme", "-x264-params", "interlaced=1:tff=1", "-c:a", "mp2");

        upstream.serve("/channels.m3u", 200, """
                #EXTM3U
                #EXTINF:-1,Live
                %s
                #EXTINF:-1,MPEG-2
                %s
                #EXTINF:-1,Unwatched MPEG-2
                %s
                #EXTINF:-1,Anamorphic
                %s
                #EXTINF:-1,Broken
                %s
                #EXTINF:-1,Local file
                file:///etc/hosts
                """.formatted(upstream.url("/live.ts"), upstream.url("/mpeg2.ts"), upstream.url("/mpeg2-unwatched.ts"),
                upstream.url("/anamorphic.ts"), upstream.url("/missing.ts")));

        server = TestServer.start(tempDir, """
                [streaming]
                idle-timeout-seconds = 2

                [transcoding]
                preset = "ultrafast"

                [[transcoding.profiles]]
                id = "low"
                name = "Low"
                height = 180
                bitrate-kbps = 300

                [[transcoding.profiles]]
                id = "high"
                name = "High"
                height = 720
                bitrate-kbps = 2000
                """);
        admin = server.login("admin-1", "admin", List.of("admins"));
        viewer = server.login("viewer-1", "viewer", List.of());
        otherViewer = server.login("viewer-2", "other", List.of());
        transcoder = server.login("transcoder-1", "transcoder", List.of("transcoders"));

        JsonObject body = new JsonObject();
        body.addProperty("name", "Test channels");
        body.addProperty("url", upstream.url("/channels.m3u"));
        assertEquals(201, server.send("POST", "/api/playlists", admin, body.toString()).statusCode());
        for (JsonElement channel : json(server.get("/api/channels", viewer)).getAsJsonArray()) {
            channelIds.put(channel.getAsJsonObject().get("name").getAsString(), channel.getAsJsonObject().get("id").getAsString());
        }
    }

    @AfterAll
    static void stopServers() {
        if (server != null) {
            server.close();
        }
        if (upstream != null) {
            upstream.close();
        }
    }

    @Test
    void streamsChannelAsFragmentedMp4WithAacAudio() throws Exception {
        JsonObject stream = startStream(viewer, "Live", null, null);
        assertEquals(channelIds.get("Live"), stream.get("channelId").getAsString());
        assertEquals("auto", stream.get("quality").getAsString());
        assertFalse(stream.get("transcoding").getAsBoolean());
        assertEquals("h264", stream.get("sourceVideoCodec").getAsString());
        assertFalse(stream.get("sourceInterlaced").getAsBoolean());

        HttpResponse<String> playlist = server.get(stream.get("playlistUrl").getAsString(), viewer);
        assertEquals(200, playlist.statusCode());
        assertEquals("application/vnd.apple.mpegurl", playlist.headers().firstValue("content-type").orElseThrow());
        assertTrue(playlist.body().contains("#EXT-X-MAP:URI=\"init.mp4\""), playlist.body());

        assertEquals(List.of("h264,320,240,1:1", "aac"), probe(viewer, stream), "Video should be copied, AC-3 audio converted to AAC");
        stopStream(viewer, stream);
    }

    @Test
    void transcodesCodecsTheBrowserCannotPlay() throws Exception {
        // Kijkbuis doesn't know this channel's codec yet, so it starts passing the video through, and has to restart
        // ffmpeg once it sees the channel is MPEG-2
        JsonObject stream = startStream(transcoder, "Unwatched MPEG-2", null, null);
        assertTrue(stream.get("transcoding").getAsBoolean());
        assertEquals("mpeg2video", stream.get("sourceVideoCodec").getAsString());
        assertEquals("h264,320,240,1:1", probe(transcoder, stream).getFirst());
        stopStream(transcoder, stream);

        // Now Kijkbuis knows the channel's codec, so it transcodes right away
        JsonObject again = startStream(transcoder, "Unwatched MPEG-2", null, null);
        assertTrue(again.get("transcoding").getAsBoolean());
        assertEquals("h264,320,240,1:1", probe(transcoder, again).getFirst());
        stopStream(transcoder, again);
    }

    @Test
    void usersWithoutTranscodePrivilegeCannotWatchUnplayableCodecs() throws Exception {
        HttpResponse<String> response = server.send("POST", "/api/streams", viewer, startJson("MPEG-2", null, null));
        assertEquals(422, response.statusCode(), response.body());
        assertTrue(json(response).getAsJsonObject().get("error").getAsString().contains("mpeg2video"));
    }

    @Test
    void passesThroughCodecsTheBrowserSaysItCanPlay() throws Exception {
        JsonObject stream = startStream(transcoder, "Live", null, List.of("hvc1", "avc1"));
        assertFalse(stream.get("transcoding").getAsBoolean());
        stopStream(transcoder, stream);
    }

    @Test
    void transcodesToPickedQualityWithSquarePixels() throws Exception {
        JsonObject stream = startStream(transcoder, "Anamorphic", "low", null);
        assertEquals("low", stream.get("quality").getAsString());
        assertTrue(stream.get("transcoding").getAsBoolean());
        // 720x576 shown as 16:9 becomes 320x180 with square pixels, deinterlaced
        assertEquals("h264,320,180,1:1", probe(transcoder, stream).getFirst());
        assertEquals(1024, stream.get("sourceWidth").getAsInt(), "The size it's shown at, not the size it's sent at");
        assertEquals(576, stream.get("sourceHeight").getAsInt());
        stopStream(transcoder, stream);
    }

    @Test
    void deinterlacesInterlacedChannelsForUsersWhoMayTranscode() throws Exception {
        // Browsers can play interlaced H.264, if not nicely, so users who may not transcode still get to watch it
        JsonObject passthrough = startStream(viewer, "Anamorphic", null, null);
        assertFalse(passthrough.get("transcoding").getAsBoolean());
        assertTrue(passthrough.get("sourceInterlaced").getAsBoolean());
        stopStream(viewer, passthrough);

        // Users who may transcode get it deinterlaced, with a frame for every field
        JsonObject deinterlaced = startStream(transcoder, "Anamorphic", null, null);
        assertTrue(deinterlaced.get("transcoding").getAsBoolean());
        assertEquals("50/1", frameRate(transcoder, deinterlaced));
        stopStream(transcoder, deinterlaced);

        // Progressive channels keep their frame rate when transcoded
        JsonObject progressive = startStream(transcoder, "Live", "high", null);
        assertFalse(progressive.get("sourceInterlaced").getAsBoolean());
        assertEquals("25/1", frameRate(transcoder, progressive));
        stopStream(transcoder, progressive);
    }

    @Test
    void doesNotUpscale() throws Exception {
        JsonObject stream = startStream(transcoder, "Live", "high", null);
        assertEquals("h264,320,240,1:1", probe(transcoder, stream).getFirst());
        stopStream(transcoder, stream);
    }

    @Test
    void onlyUsersWithTranscodePrivilegeCanPickQuality() throws Exception {
        HttpResponse<String> response = server.send("POST", "/api/streams", viewer, startJson("Live", "low", null));
        assertEquals(403, response.statusCode(), response.body());

        JsonArray profiles = json(server.get("/api/profiles", viewer)).getAsJsonArray();
        assertEquals(2, profiles.size());
        assertEquals("low", profiles.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(180, profiles.get(0).getAsJsonObject().get("height").getAsInt());
        assertEquals(320, profiles.get(0).getAsJsonObject().get("width").getAsInt());
        assertEquals(300, profiles.get(0).getAsJsonObject().get("bitrateKbps").getAsInt());
    }

    @Test
    void playbackUrlsWorkWithoutSessionButOnlyOwnersCanStop() throws Exception {
        JsonObject stream = startStream(viewer, "Live", null, null);
        String id = stream.get("id").getAsString();
        String playlistUrl = stream.get("playlistUrl").getAsString();
        assertTrue(playlistUrl.startsWith("/live/"), playlistUrl);

        // The token in the URL is all a player needs, so players that can't send cookies (VLC, Chromecast) work too
        assertEquals(200, server.get(playlistUrl, null).statusCode());
        assertEquals(404, server.get("/live/made-up-token/" + Stream.PLAYLIST, null).statusCode());
        assertEquals(404, server.send("DELETE", "/api/streams/" + id, otherViewer, null).statusCode());

        // Segments are already compressed video, so they aren't gzipped
        HttpResponse<byte[]> uncompressed = HTTP.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + playlistUrl))
                .header("Accept-Encoding", "gzip").build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, uncompressed.statusCode());
        assertTrue(uncompressed.headers().firstValue("content-encoding").isEmpty());

        stopStream(viewer, stream);
        assertEquals(404, server.get(playlistUrl, null).statusCode());
    }

    @Test
    void adminsSeeAndStopEveryonesStreams() throws Exception {
        JsonObject stream = startStream(viewer, "Live", null, null);
        String id = stream.get("id").getAsString();
        assertEquals(403, server.get("/api/streams", viewer).statusCode());

        JsonObject listed = null;
        for (JsonElement element : json(server.get("/api/streams", admin)).getAsJsonArray()) {
            if (element.getAsJsonObject().get("id").getAsString().equals(id)) {
                listed = element.getAsJsonObject();
            }
        }
        assertNotNull(listed);
        assertEquals("viewer", listed.getAsJsonObject("user").get("username").getAsString());
        assertEquals("Live", listed.get("channelName").getAsString());
        assertTrue(listed.has("startedAt"));

        assertEquals(404, server.send("DELETE", "/api/streams/" + id, otherViewer, null).statusCode(), "Only admins can stop other people's streams");
        assertEquals(204, server.send("DELETE", "/api/streams/" + id, admin, null).statusCode());
        // The player is told an admin stopped it, so it doesn't start a new stream like it would after a hiccup
        HttpResponse<String> kicked = server.get(stream.get("playlistUrl").getAsString(), viewer);
        assertEquals(410, kicked.statusCode());
        assertEquals("An admin stopped this stream", json(kicked).getAsJsonObject().get("error").getAsString());
    }

    @Test
    void loggingOutStopsThatSessionsStreams() throws Exception {
        String phone = server.login("viewer-4", "twice", List.of());
        String laptop = server.login("viewer-4", "twice", List.of());
        JsonObject onPhone = startStream(phone, "Live", null, null);
        JsonObject onLaptop = startStream(laptop, "Live", null, null);

        // Playback URLs work without a session, so logging out alone wouldn't stop the stream
        assertEquals(204, server.send("POST", "/api/auth/logout", phone, null).statusCode());
        assertEquals(404, server.get(onPhone.get("playlistUrl").getAsString(), null).statusCode());
        // The same user keeps watching on their other device
        assertEquals(200, server.get(onLaptop.get("playlistUrl").getAsString(), null).statusCode());
        stopStream(laptop, onLaptop);
    }

    @Test
    void signingSomeoneOutStopsTheirStreams() throws Exception {
        String leaver = server.login("viewer-3", "leaver", List.of());
        JsonObject stream = startStream(leaver, "Live", null, null);
        String userId = json(server.get("/api/me", leaver)).getAsJsonObject().get("id").getAsString();

        // Playback URLs work without a session, so signing out alone wouldn't stop the stream
        assertEquals(204, server.send("DELETE", "/api/users/" + userId + "/sessions", admin, null).statusCode());
        assertEquals(410, server.get(stream.get("playlistUrl").getAsString(), null).statusCode());
    }

    @Test
    void stoppingAStreamStopsFfmpeg() throws Exception {
        JsonObject started = startStream(viewer, "Live", null, null);
        Stream stream = server.app().streamManager().find(UUID.fromString(started.get("id").getAsString()));
        assertNotNull(stream);
        assertTrue(stream.isRunning());

        stopStream(viewer, started);
        assertEquals(404, server.get(started.get("playlistUrl").getAsString(), viewer).statusCode());
        awaitTrue(() -> !stream.isRunning(), "ffmpeg should stop with the stream");
    }

    @Test
    void idleStreamsAreStopped() throws Exception {
        JsonObject started = startStream(viewer, "Live", null, null);
        UUID id = UUID.fromString(started.get("id").getAsString());
        Stream stream = server.app().streamManager().find(id);
        assertNotNull(stream);

        // Nothing requests the stream, so it should be stopped after the 2 second idle timeout
        awaitTrue(() -> server.app().streamManager().find(id) == null, "Idle stream should be stopped");
        awaitTrue(() -> !stream.isRunning(), "ffmpeg should stop with the stream");
    }

    @Test
    void reportsChannelsThatCannotBeStarted() throws Exception {
        HttpResponse<String> broken = server.send("POST", "/api/streams", viewer, startJson("Broken", null, null));
        assertEquals(502, broken.statusCode(), broken.body());
        assertEquals("The channel could not be started", json(broken).getAsJsonObject().get("error").getAsString());

        HttpResponse<String> localFile = server.send("POST", "/api/streams", viewer, startJson("Local file", null, null));
        assertEquals(502, localFile.statusCode(), localFile.body());
        assertEquals("This channel's stream URL is not supported", json(localFile).getAsJsonObject().get("error").getAsString());
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        assertEquals(404, server.send("POST", "/api/streams", viewer, "{\"channelId\": \"" + UUID.randomUUID() + "\"}").statusCode());
        assertEquals(400, server.send("POST", "/api/streams", viewer, "{}").statusCode());
        assertEquals(400, server.send("POST", "/api/streams", viewer, "{\"channelId\": 5}").statusCode());
        assertEquals(400, server.send("POST", "/api/streams", transcoder, startJson("Live", "ultra", null)).statusCode());
        assertEquals(400, server.send("POST", "/api/streams", viewer, "{\"channelId\": \"" + channelIds.get("Live") + "\", \"videoCodecs\": \"h264\"}").statusCode());
        assertEquals(401, server.send("POST", "/api/streams", null, startJson("Live", null, null)).statusCode());
        assertEquals(404, server.send("DELETE", "/api/streams/" + UUID.randomUUID(), viewer, null).statusCode());
    }

    /**
     * Generates a 12 second sample and serves it at twice real-time, to keep the tests quick while still behaving
     * like a live channel.
     */
    private static void serveSample(@NotNull String path, @NotNull String video, @NotNull String... encoding) throws Exception {
        Path sample = tempDir.resolve(path.substring(1));
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", video, "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=48000", "-t", String.valueOf(SAMPLE_SECONDS)));
        command.addAll(List.of(encoding));
        command.addAll(List.of("-f", "mpegts", sample.toString()));
        assumeTrue(succeeds(command.toArray(String[]::new)), "ffmpeg can't generate the " + path + " sample");

        byte[] bytes = Files.readAllBytes(sample);
        upstream.serveStream(path, bytes, bytes.length / SAMPLE_SECONDS * 2);
    }

    @NotNull
    private static JsonObject startStream(@NotNull String session, @NotNull String channelName, @Nullable String quality, @Nullable List<String> videoCodecs) throws Exception {
        HttpResponse<String> response = server.send("POST", "/api/streams", session, startJson(channelName, quality, videoCodecs));
        assertEquals(201, response.statusCode(), response.body());
        return json(response).getAsJsonObject();
    }

    private static void stopStream(@NotNull String session, @NotNull JsonObject stream) throws Exception {
        assertEquals(204, server.send("DELETE", "/api/streams/" + stream.get("id").getAsString(), session, null).statusCode());
    }

    @NotNull
    private static String startJson(@NotNull String channelName, @Nullable String quality, @Nullable List<String> videoCodecs) {
        JsonObject body = new JsonObject();
        body.addProperty("channelId", channelIds.get(channelName));
        if (quality != null) {
            body.addProperty("quality", quality);
        }
        if (videoCodecs != null) {
            JsonArray codecs = new JsonArray();
            videoCodecs.forEach(codecs::add);
            body.add("videoCodecs", codecs);
        }
        return body.toString();
    }

    /**
     * Downloads the stream's init segment and first media segment, and asks ffprobe what's in them.
     *
     * @return the video stream as codec,width,height,sample aspect ratio, followed by the audio codec
     */
    @NotNull
    private static List<String> probe(@NotNull String session, @NotNull JsonObject stream) throws Exception {
        String output = ffprobe(download(session, stream), "-show_entries", "stream=codec_type,codec_name,width,height,sample_aspect_ratio");

        // ffprobe lists fields in its own order: codec_name,codec_type,width,height,sample_aspect_ratio
        List<String> streams = new ArrayList<>();
        for (String line : output.strip().split("\\R")) {
            String[] fields = line.split(",");
            streams.add(fields[1].equals("video") ? String.join(",", fields[0], fields[2], fields[3], fields[4]) : fields[0]);
        }
        return streams;
    }

    /**
     * @return the video's frame rate in the stream's first media segment, such as 25/1
     */
    @NotNull
    private static String frameRate(@NotNull String session, @NotNull JsonObject stream) throws Exception {
        return ffprobe(download(session, stream), "-select_streams", "v:0", "-show_entries", "stream=r_frame_rate").strip();
    }

    /**
     * Downloads the stream's init segment and first media segment into one file.
     */
    @NotNull
    private static Path download(@NotNull String session, @NotNull JsonObject stream) throws Exception {
        String playlistUrl = stream.get("playlistUrl").getAsString();
        String playlist = server.get(playlistUrl, session).body();
        String firstSegment = playlist.lines().filter((line) -> !line.startsWith("#") && !line.isBlank()).findFirst().orElseThrow();
        String streamPath = playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1);

        byte[] init = file(session, streamPath + "init.mp4");
        assertEquals("ftyp", new String(init, 4, 4, StandardCharsets.US_ASCII));
        Path media = tempDir.resolve("probe-" + UUID.randomUUID() + ".mp4");
        Files.write(media, init);
        Files.write(media, file(session, streamPath + firstSegment), StandardOpenOption.APPEND);
        return media;
    }

    /**
     * @return ffprobe's output as CSV
     */
    @NotNull
    private static String ffprobe(@NotNull Path media, @NotNull String... options) throws Exception {
        List<String> command = new ArrayList<>(List.of("ffprobe", "-v", "error"));
        command.addAll(List.of(options));
        command.addAll(List.of("-of", "csv=p=0", media.toString()));
        Process probe = new ProcessBuilder(command).start();
        String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, probe.waitFor());
        return output;
    }

    private static byte @NotNull [] file(@NotNull String session, @NotNull String path) throws Exception {
        HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).header("Cookie", session).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), path);
        assertEquals("video/mp4", response.headers().firstValue("content-type").orElseThrow());
        return response.body();
    }

    private static void awaitTrue(@NotNull BooleanSupplier condition, @NotNull String message) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean()) {
            assertTrue(Instant.now().isBefore(deadline), message);
            Thread.sleep(100);
        }
    }

    private static boolean succeeds(@NotNull String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
