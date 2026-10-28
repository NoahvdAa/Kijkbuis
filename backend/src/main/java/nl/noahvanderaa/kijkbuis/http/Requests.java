package nl.noahvanderaa.kijkbuis.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class Requests {

    private static final int MAX_JSON_BODY_SIZE = 64 * 1024;

    private Requests() {
    }

    /**
     * Reads the request body, which must be a JSON object.
     */
    @NotNull
    public static JsonObject jsonBody(@NotNull HttpServerExchange exchange) throws IOException {
        byte[] body = exchange.getInputStream().readNBytes(MAX_JSON_BODY_SIZE + 1);
        if (body.length > MAX_JSON_BODY_SIZE) {
            throw new ApiException(StatusCodes.REQUEST_ENTITY_TOO_LARGE, "The request body is too large");
        }

        try {
            JsonElement element = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (JsonParseException ignored) {
            // Handled below
        }
        throw new ApiException(StatusCodes.BAD_REQUEST, "The request body must be a JSON object");
    }

    /**
     * Reads a UUID from the request path. An invalid UUID can't belong to anything, so it's treated as not found.
     *
     * @param name            the name of the path parameter
     * @param notFoundMessage the message to respond with when the UUID is invalid
     */
    @NotNull
    public static UUID pathUuid(@NotNull HttpServerExchange exchange, @NotNull String name, @NotNull String notFoundMessage) {
        try {
            return UUID.fromString(pathParam(exchange, name));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ApiException(StatusCodes.NOT_FOUND, notFoundMessage);
        }
    }

    @NotNull
    public static String pathParam(@NotNull HttpServerExchange exchange, @NotNull String name) {
        return exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).getParameters().get(name);
    }

    @Nullable
    public static String queryParam(@NotNull HttpServerExchange exchange, @NotNull String name) {
        Deque<String> values = exchange.getQueryParameters().get(name);
        return values != null ? values.peekFirst() : null;
    }

    /**
     * @return all values of a query parameter that may be given several times
     */
    @NotNull
    public static List<String> queryParams(@NotNull HttpServerExchange exchange, @NotNull String name) {
        Deque<String> values = exchange.getQueryParameters().get(name);
        return values != null ? List.copyOf(values) : List.of();
    }

    /**
     * Reads an optional UUID from the query string.
     *
     * @return the UUID, or null if the parameter isn't given
     */
    @Nullable
    public static UUID queryUuid(@NotNull HttpServerExchange exchange, @NotNull String name) {
        String value = queryParam(exchange, name);
        return value != null ? parseUuid(name, value) : null;
    }

    /**
     * Reads an optional timestamp, such as {@code 2026-10-26T20:00:00Z}, from the query string.
     *
     * @return the timestamp, or null if the parameter isn't given
     */
    @Nullable
    public static Instant queryInstant(@NotNull HttpServerExchange exchange, @NotNull String name) {
        String value = queryParam(exchange, name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ApiException(StatusCodes.BAD_REQUEST, name + " must be a timestamp such as 2026-10-26T20:00:00Z");
        }
    }

    @NotNull
    public static UUID parseUuid(@NotNull String name, @NotNull String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(StatusCodes.BAD_REQUEST, name + " must be an ID");
        }
    }

}
