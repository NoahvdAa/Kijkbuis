package nl.noahvanderaa.kijkbuis.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public final class Responses {

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

    public static void redirect(@NotNull HttpServerExchange exchange, @NotNull String location) {
        exchange.setStatusCode(StatusCodes.FOUND);
        exchange.getResponseHeaders().put(Headers.LOCATION, location);
        exchange.endExchange();
    }

}
