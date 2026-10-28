package nl.noahvanderaa.kijkbuis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

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
        this.responses.put(path, new Response(status, body, 0));
    }

    /**
     * Serves a body slowly, like a live stream, until the client disconnects or the body runs out.
     */
    void serveStream(@NotNull String path, byte @NotNull [] body, int bytesPerSecond) {
        this.responses.put(path, new Response(200, body, bytesPerSecond));
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
        Response response = this.responses.getOrDefault(path, new Response(404, "Not found".getBytes(StandardCharsets.UTF_8), 0));
        if (response.bytesPerSecond() <= 0) {
            String etag = "\"" + Integer.toHexString(Arrays.hashCode(response.body())) + "\"";
            int status = response.status() == 200 && etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match")) ? 304 : response.status();
            this.statuses.computeIfAbsent(path, (key) -> new CopyOnWriteArrayList<>()).add(status);

            if (response.status() == 200) {
                exchange.getResponseHeaders().add("ETag", etag);
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

    private record Response(int status, byte @NotNull [] body, int bytesPerSecond) {
    }

}
