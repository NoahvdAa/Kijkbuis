package nl.noahvanderaa.kijkbuis.http.handler.stream;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.stream.Stream;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Serves a stream's HLS playlist, init segment and media segments to the player. These requests don't need a session:
 * the playback token in the URL is only handed to the stream's owner, and is unguessable.
 */
public class GetStreamFileHandler implements HttpHandler {

    private final KijkbuisApp app;

    public GetStreamFileHandler(@NotNull KijkbuisApp app) {
        this.app = app;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        String token = Requests.pathParam(exchange, "token");
        Stream stream = this.app.streamManager().findByPlaybackToken(token);
        if (stream == null) {
            if (this.app.streamManager().wasKicked(token)) {
                // Tells the player not to start a new stream
                throw new ApiException(StatusCodes.GONE, "An admin stopped this stream");
            }
            throw new ApiException(StatusCodes.NOT_FOUND, "Stream not found");
        }

        String name = Requests.pathParam(exchange, "file");
        byte[] data = stream.file(name);
        if (data == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "File not found");
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType(name));
        // The playlist changes every segment, segments themselves never change
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, name.equals(Stream.PLAYLIST) ? "no-cache" : "private, max-age=600");
        exchange.getResponseSender().send(ByteBuffer.wrap(data));
    }

    @NotNull
    private static String contentType(@NotNull String name) {
        if (name.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (name.endsWith(".mp4") || name.endsWith(".m4s")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

}
