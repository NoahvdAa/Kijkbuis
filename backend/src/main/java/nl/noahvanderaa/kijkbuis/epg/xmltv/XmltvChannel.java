package nl.noahvanderaa.kijkbuis.epg.xmltv;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param id          the channel ID, which playlists refer to with tvg-id
 * @param displayName the first display-name
 * @param iconUrl     the first icon
 */
public record XmltvChannel(@NotNull String id, @Nullable String displayName, @Nullable String iconUrl) implements XmltvEntry {
}
