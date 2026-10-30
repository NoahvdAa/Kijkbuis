package nl.noahvanderaa.kijkbuis.http;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown to abort a request with the given status code. The message is shown to the client, so it must not contain
 * anything sensitive.
 */
public class ApiException extends RuntimeException {

    private final int statusCode;

    public ApiException(int statusCode, @NotNull String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return this.statusCode;
    }

}
