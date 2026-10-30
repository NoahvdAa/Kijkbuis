package nl.noahvanderaa.kijkbuis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Serves playlists, EPGs and streams for tests. Unknown paths respond with 404. Regular responses have an ETag, and
 * requests with a matching If-None-Match get a 304.
 */
final class MockPlaylistServer implements AutoCloseable {

    private static final int CHUNKS_PER_SECOND = 10;

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> statuses = new ConcurrentHashMap<>();

    MockPlaylistServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    void serve(@NotNull String path, int status, @NotNull String body) {
        this.serve(path, status, body.getBytes(StandardCharsets.UTF_8));
    }

    void serve(@NotNull String path, int status, byte @NotNull [] body) {
        this.serve(path, status, null, body);
    }

    /**
     * @param contentType the Content-Type to respond with, or null to leave it out
     */
    void serve(@NotNull String path, int status, @Nullable String contentType, byte @NotNull [] body) {
        this.responses.put(path, new Response(status, body, 0, contentType));
    }

    /**
     * Serves a body slowly, like a live stream, until the client disconnects or the body runs out.
     */
    void serveStream(@NotNull String path, byte @NotNull [] body, int bytesPerSecond) {
        this.responses.put(path, new Response(200, body, bytesPerSecond, null));
    }

    /**
     * Generates a sample with ffmpeg, and serves it like a live channel. It's served at twice real-time, to keep tests
     * quick. Skips the test if ffmpeg can't generate it.
     *
     * @param dir      the directory to keep the sample in
     * @param seconds  how long the sample is
     * @param video    the lavfi source of the video, such as testsrc2=size=320x240:rate=25
     * @param encoding ffmpeg's options to encode the sample with
     */
    void serveSample(@NotNull Path dir, int seconds, @NotNull String path, @NotNull String video, @NotNull String... encoding) throws Exception {
        Path sample = dir.resolve(path.substring(1));
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", video, "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=48000", "-t", String.valueOf(seconds)));
        command.addAll(List.of(encoding));
        command.addAll(List.of("-f", "mpegts", sample.toString()));
        Process ffmpeg;
        try {
            ffmpeg = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException ex) {
            ffmpeg = abort("ffmpeg is not installed");
        }
        assumeTrue(ffmpeg.waitFor() == 0, "ffmpeg can't generate the " + path + " sample");

        byte[] bytes = Files.readAllBytes(sample);
        this.serveStream(path, bytes, bytes.length / seconds * 2);
    }

    /**
     * @return the status codes of the responses to requests for the path so far
     */
    @NotNull
    List<Integer> statuses(@NotNull String path) {
        return new ArrayList<>(this.statuses.getOrDefault(path, List.of()));
    }

    @NotNull
    String url(@NotNull String path) {
        return "http://localhost:" + this.server.getAddress().getPort() + path;
    }

    @Override
    public void close() {
        this.server.stop(0);
    }

    private void handle(@NotNull HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Response response = this.responses.getOrDefault(path, new Response(404, "Not found".getBytes(StandardCharsets.UTF_8), 0, null));
        if (response.bytesPerSecond() <= 0) {
            String etag = "\"" + Integer.toHexString(Arrays.hashCode(response.body())) + "\"";
            int status = response.status() == 200 && etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match")) ? 304 : response.status();
            this.statuses.computeIfAbsent(path, (key) -> new CopyOnWriteArrayList<>()).add(status);

            if (response.status() == 200) {
                exchange.getResponseHeaders().add("ETag", etag);
            }
            if (response.contentType() != null) {
                exchange.getResponseHeaders().add("Content-Type", response.contentType());
            }
            byte[] body = status == 304 ? new byte[0] : response.body();
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            return;
        }

        this.statuses.computeIfAbsent(path, (key) -> new CopyOnWriteArrayList<>()).add(response.status());
        exchange.sendResponseHeaders(response.status(), 0);
        int chunkSize = Math.max(1, response.bytesPerSecond() / CHUNKS_PER_SECOND);
        try (OutputStream out = exchange.getResponseBody()) {
            for (int offset = 0; offset < response.body().length; offset += chunkSize) {
                out.write(response.body(), offset, Math.min(chunkSize, response.body().length - offset));
                out.flush();
                Thread.sleep(1000 / CHUNKS_PER_SECOND);
            }
        } catch (IOException | InterruptedException ignored) {
            // The client went away
        }
    }

    private record Response(int status, byte @NotNull [] body, int bytesPerSecond, @Nullable String contentType) {
    }

}
