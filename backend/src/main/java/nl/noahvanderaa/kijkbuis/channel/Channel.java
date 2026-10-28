package nl.noahvanderaa.kijkbuis.channel;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import nl.noahvanderaa.kijkbuis.user.Privilege;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A channel loaded from a playlist.
 *
 * @param id            the channel ID, which stays the same across playlist refreshes
 * @param playlistId    the playlist the channel comes from
 * @param name          the channel name
 * @param groupTitle    the group the playlist puts the channel in
 * @param logoUrl       the URL of the channel logo
 * @param streamUrl     the upstream stream URL, which usually contains provider credentials
 * @param tvgId              the ID the playlist uses to match the channel with EPG data
 * @param epgChannelOverride the EPG channel an admin mapped this channel to, used instead of the tvg-id
 * @param channelNumber      the channel number from the playlist
 */
public record Channel(
        @NotNull UUID id,
        @NotNull UUID playlistId,
        @NotNull String name,
        @Nullable String groupTitle,
        @Nullable String logoUrl,
        @NotNull String streamUrl,
        @Nullable String tvgId,
        @Nullable String epgChannelOverride,
        @Nullable Integer channelNumber
) implements KBSerializable {

    /**
     * @return the ID of the EPG channel this channel gets its programmes from, if any
     */
    @Nullable
    public String epgChannelId() {
        return this.epgChannelOverride != null ? this.epgChannelOverride : this.tvgId;
    }

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = new JsonObject();

        obj.addProperty("id", this.id.toString());
        obj.addProperty("playlistId", this.playlistId.toString());
        obj.addProperty("name", this.name);
        obj.addProperty("group", this.groupTitle);
        obj.addProperty("logoUrl", this.logoUrl);
        obj.addProperty("channelNumber", this.channelNumber);
        obj.addProperty("tvgId", this.tvgId);
        obj.addProperty("epgChannelId", this.epgChannelId());

        if (user.hasPrivilege(Privilege.ADMIN)) {
            obj.addProperty("streamUrl", this.streamUrl);
            obj.addProperty("epgChannelOverride", this.epgChannelOverride);
        }

        return obj;
    }

}
