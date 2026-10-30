package nl.noahvanderaa.kijkbuis.playlist;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Changes to make to a playlist. Null values are left unchanged, except for the EPG URL which can be cleared, so it
 * has a separate flag.
 *
 * @param name            the new name
 * @param url             the new M3U URL
 * @param epgUrlChanged   whether the EPG URL should be changed
 * @param epgUrl          the new EPG URL, or null to clear it
 * @param refreshInterval the new refresh interval
 */
public record PlaylistChanges(
        @Nullable String name,
        @Nullable String url,
        boolean epgUrlChanged,
        @Nullable String epgUrl,
        @Nullable Duration refreshInterval
) {
}
