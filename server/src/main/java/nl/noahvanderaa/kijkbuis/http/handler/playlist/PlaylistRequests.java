package nl.noahvanderaa.kijkbuis.http.handler.playlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.playlist.PlaylistChanges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * Reads and validates playlist fields from request bodies.
 */
final class PlaylistRequests {

    static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofHours(12);
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MIN_REFRESH_INTERVAL_MINUTES = 5;
    private static final int MAX_REFRESH_INTERVAL_MINUTES = 7 * 24 * 60;

    private PlaylistRequests() {
    }

    /**
     * Reads the playlist fields from a request body. Fields that are missing from the body are left null.
     */
    @NotNull
    static PlaylistChanges parseChanges(@NotNull JsonObject body) {
        String name = body.has("name") ? name(body.get("name")) : null;
        String url = body.has("url") ? url("url", body.get("url")) : null;
        boolean epgUrlChanged = body.has("epgUrl");
        String epgUrl = epgUrlChanged ? optionalUrl("epgUrl", body.get("epgUrl")) : null;
        Duration refreshInterval = body.has("refreshIntervalMinutes") ? refreshInterval(body.get("refreshIntervalMinutes")) : null;
        return new PlaylistChanges(name, url, epgUrlChanged, epgUrl, refreshInterval);
    }

    @NotNull
    private static String name(@NotNull JsonElement element) {
        String name = string("name", element).strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ApiException(StatusCodes.BAD_REQUEST, "name must be between 1 and " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    @Nullable
    private static String optionalUrl(@NotNull String field, @NotNull JsonElement element) {
        if (element.isJsonNull() || (element.isJsonPrimitive() && element.getAsString().isBlank())) {
            return null;
        }
        return url(field, element);
    }

    @NotNull
    private static String url(@NotNull String field, @NotNull JsonElement element) {
        String url = string(field, element).strip();
        try {
            URI uri = new URI(url);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null) {
                return url;
            }
        } catch (URISyntaxException ignored) {
            // Handled below
        }
        throw new ApiException(StatusCodes.BAD_REQUEST, field + " must be an http:// or https:// URL");
    }

    @NotNull
    private static Duration refreshInterval(@NotNull JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                int minutes = element.getAsBigDecimal().intValueExact();
                if (minutes >= MIN_REFRESH_INTERVAL_MINUTES && minutes <= MAX_REFRESH_INTERVAL_MINUTES) {
                    return Duration.ofMinutes(minutes);
                }
            } catch (ArithmeticException ignored) {
                // Handled below
            }
        }
        throw new ApiException(StatusCodes.BAD_REQUEST, "refreshIntervalMinutes must be a whole number between "
                + MIN_REFRESH_INTERVAL_MINUTES + " and " + MAX_REFRESH_INTERVAL_MINUTES);
    }

    @NotNull
    private static String string(@NotNull String field, @NotNull JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new ApiException(StatusCodes.BAD_REQUEST, field + " must be a string");
        }
        return element.getAsString();
    }

}
