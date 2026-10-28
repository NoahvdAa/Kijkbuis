package nl.noahvanderaa.kijkbuis.playlist.m3u;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single entry of an M3U playlist.
 *
 * @param name          the title, falling back to tvg-name and then the URL
 * @param url           the stream URL
 * @param tvgId         the ID used to match the entry with EPG data
 * @param logoUrl       the tvg-logo
 * @param groupTitle    the group-title, falling back to #EXTGRP
 * @param channelNumber the tvg-chno
 */
public record M3UEntry(
        @NotNull String name,
        @NotNull String url,
        @Nullable String tvgId,
        @Nullable String logoUrl,
        @Nullable String groupTitle,
        @Nullable Integer channelNumber
) {
}
