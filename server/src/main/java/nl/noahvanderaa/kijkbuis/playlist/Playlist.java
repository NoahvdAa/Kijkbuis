package nl.noahvanderaa.kijkbuis.playlist;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An M3U playlist that channels are loaded from.
 *
 * @param id                      the playlist ID
 * @param name                    the name shown to users
 * @param url                     the URL of the M3U file, which usually contains provider credentials
 * @param epgUrl                  the XMLTV URL set by an admin, if any
 * @param detectedEpgUrl          the XMLTV URL advertised in the playlist's header, if any
 * @param refreshInterval         how often the playlist is refreshed
 * @param lastRefreshAttemptAt    when a refresh was last attempted, successful or not
 * @param lastRefreshedAt         when the playlist was last refreshed successfully
 * @param lastRefreshError        why the last refresh attempt failed, or null if it succeeded
 * @param epgLastRefreshAttemptAt when an EPG refresh was last attempted, successful or not
 * @param epgLastRefreshedAt      when the EPG was last refreshed successfully
 * @param epgLastRefreshError     why the last EPG refresh attempt failed, or null if it succeeded
 * @param epgRefreshing           whether the EPG is being refreshed right now
 * @param channelCount            the number of channels in the playlist
 * @param createdAt               when the playlist was added
 */
public record Playlist(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull String url,
        @Nullable String epgUrl,
        @Nullable String detectedEpgUrl,
        @NotNull Duration refreshInterval,
        @Nullable Instant lastRefreshAttemptAt,
        @Nullable Instant lastRefreshedAt,
        @Nullable String lastRefreshError,
        @Nullable Instant epgLastRefreshAttemptAt,
        @Nullable Instant epgLastRefreshedAt,
        @Nullable String epgLastRefreshError,
        boolean epgRefreshing,
        int channelCount,
        @NotNull Instant createdAt
) implements KBSerializable {

    /**
     * Failed refreshes are retried sooner than the regular interval, but not so often that we hammer the provider.
     */
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(15);

    @NotNull
    public Instant nextRefreshAt() {
        if (this.lastRefreshAttemptAt == null) {
            return this.createdAt;
        }
        boolean retry = this.lastRefreshError != null && RETRY_INTERVAL.compareTo(this.refreshInterval) < 0;
        return this.lastRefreshAttemptAt.plus(retry ? RETRY_INTERVAL : this.refreshInterval);
    }

    /**
     * @return the EPG URL that's used: the one set by an admin, or else the one advertised by the playlist
     */
    @Nullable
    public String activeEpgUrl() {
        return this.epgUrl != null ? this.epgUrl : this.detectedEpgUrl;
    }

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = new JsonObject();

        obj.addProperty("id", this.id.toString());
        obj.addProperty("name", this.name);
        obj.addProperty("channelCount", this.channelCount);

        if (user.hasPrivilege(Privilege.ADMIN)) {
            obj.addProperty("url", this.url);
            obj.addProperty("epgUrl", this.epgUrl);
            obj.addProperty("detectedEpgUrl", this.detectedEpgUrl);
            obj.addProperty("activeEpgUrl", this.activeEpgUrl());
            obj.addProperty("refreshIntervalMinutes", this.refreshInterval.toMinutes());
            obj.addProperty("lastRefreshAttemptAt", toString(this.lastRefreshAttemptAt));
            obj.addProperty("lastRefreshedAt", toString(this.lastRefreshedAt));
            obj.addProperty("lastRefreshError", this.lastRefreshError);
            obj.addProperty("nextRefreshAt", this.nextRefreshAt().toString());
            obj.addProperty("epgLastRefreshAttemptAt", toString(this.epgLastRefreshAttemptAt));
            obj.addProperty("epgLastRefreshedAt", toString(this.epgLastRefreshedAt));
            obj.addProperty("epgLastRefreshError", this.epgLastRefreshError);
            obj.addProperty("epgRefreshing", this.epgRefreshing);
            obj.addProperty("createdAt", this.createdAt.toString());
        }

        return obj;
    }

    @Nullable
    private static String toString(@Nullable Instant instant) {
        return instant != null ? instant.toString() : null;
    }

}
