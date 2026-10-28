package nl.noahvanderaa.kijkbuis.http;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Turns exceptions thrown by handlers into JSON error responses.
 */
public class ErrorHandler implements HttpHandler {

    private static final Logger LOGGER = LogManager.getLogger(ErrorHandler.class);
    private final HttpHandler next;

    public ErrorHandler(@NotNull HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        try {
            this.next.handleRequest(exchange);
        } catch (ApiException ex) {
            respond(exchange, ex.statusCode(), ex.getMessage());
        } catch (Exception ex) {
            LOGGER.error("Unhandled exception while handling {} {}", exchange.getRequestMethod(), exchange.getRequestPath(), ex);
            respond(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private static void respond(@NotNull HttpServerExchange exchange, int statusCode, @NotNull String message) {
        if (exchange.isResponseStarted()) {
            // Too late to send an error, all we can do is cut the response short
            exchange.endExchange();
            return;
        }
        Responses.error(exchange, statusCode, message);
    }

}
