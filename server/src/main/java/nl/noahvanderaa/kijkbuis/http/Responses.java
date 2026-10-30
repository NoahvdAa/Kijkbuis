package nl.noahvanderaa.kijkbuis.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class Responses {

    /**
     * Images can be SVGs, which could run scripts when opened directly rather than as an image. This stops that.
     */
    private static final String IMAGE_CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private Responses() {
    }

    public static void json(@NotNull HttpServerExchange exchange, @NotNull JsonElement body) {
        json(exchange, StatusCodes.OK, body);
    }

    public static void json(@NotNull HttpServerExchange exchange, int statusCode, @NotNull JsonElement body) {
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(body.toString(), StandardCharsets.UTF_8);
    }

    public static void error(@NotNull HttpServerExchange exchange, int statusCode, @NotNull String message) {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        json(exchange, statusCode, body);
    }

    /**
     * Sends an image that comes from somewhere else, such as a channel's logo, so it can only be shown as an image.
     * Its URL must change when the image does, since browsers keep it for a day without asking again.
     */
    public static void image(@NotNull HttpServerExchange exchange, @NotNull String contentType, byte @NotNull [] data) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "private, max-age=86400, immutable");
        exchange.getResponseHeaders().put(HttpString.tryFromString("X-Content-Type-Options"), "nosniff");
        exchange.getResponseHeaders().put(HttpString.tryFromString("Content-Security-Policy"), IMAGE_CONTENT_SECURITY_POLICY);
        exchange.getResponseSender().send(ByteBuffer.wrap(data));
    }

    public static void redirect(@NotNull HttpServerExchange exchange, @NotNull String location) {
        exchange.setStatusCode(StatusCodes.FOUND);
        exchange.getResponseHeaders().put(Headers.LOCATION, location);
        exchange.endExchange();
    }

}
