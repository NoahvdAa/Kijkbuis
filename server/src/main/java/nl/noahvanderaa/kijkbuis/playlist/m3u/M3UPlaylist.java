package nl.noahvanderaa.kijkbuis.playlist.m3u;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @param attributes the attributes of the #EXTM3U header, with lowercase keys
 * @param entries    the entries, in playlist order
 */
public record M3UPlaylist(@NotNull Map<String, String> attributes, @NotNull List<M3UEntry> entries) {

    /**
     * @return the EPG URL advertised in the header, if any. When several are listed, only the first is returned.
     */
    @Nullable
    public String epgUrl() {
        String urls = this.attributes.getOrDefault("url-tvg", this.attributes.get("x-tvg-url"));
        if (urls == null || urls.isBlank()) {
            return null;
        }
        return urls.split(",", 2)[0].strip();
    }

}
