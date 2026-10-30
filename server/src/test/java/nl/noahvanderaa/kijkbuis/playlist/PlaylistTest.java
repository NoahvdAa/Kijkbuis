package nl.noahvanderaa.kijkbuis.playlist;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaylistTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-26T12:00:00Z");
    private static final Instant ATTEMPTED_AT = CREATED_AT.plus(Duration.ofHours(1));

    @Test
    void newPlaylistsAreRefreshedRightAway() {
        assertEquals(CREATED_AT, playlist(Duration.ofHours(12), null, null).nextRefreshAt());
    }

    @Test
    void successfulPlaylistsWaitTheFullInterval() {
        assertEquals(ATTEMPTED_AT.plus(Duration.ofHours(12)), playlist(Duration.ofHours(12), ATTEMPTED_AT, null).nextRefreshAt());
    }

    @Test
    void failedPlaylistsAreRetriedSooner() {
        assertEquals(ATTEMPTED_AT.plus(Duration.ofMinutes(15)), playlist(Duration.ofHours(12), ATTEMPTED_AT, "HTTP 500").nextRefreshAt());
        assertEquals(ATTEMPTED_AT.plus(Duration.ofMinutes(10)), playlist(Duration.ofMinutes(10), ATTEMPTED_AT, "HTTP 500").nextRefreshAt());
    }

    @NotNull
    private static Playlist playlist(@NotNull Duration refreshInterval, @Nullable Instant lastRefreshAttemptAt, @Nullable String lastRefreshError) {
        return new Playlist(UUID.randomUUID(), "Test", "http://playlists.example/test.m3u", null, null, refreshInterval,
                lastRefreshAttemptAt, null, lastRefreshError, null, null, null, false, 0, CREATED_AT);
    }

}
