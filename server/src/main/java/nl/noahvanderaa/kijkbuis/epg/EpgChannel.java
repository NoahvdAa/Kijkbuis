package nl.noahvanderaa.kijkbuis.epg;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A channel listed in an EPG, which playlist channels can be mapped to.
 *
 * @param id          the ID that playlists refer to with tvg-id
 * @param displayName the name of the channel
 * @param iconUrl     the URL of the channel logo
 */
public record EpgChannel(@NotNull String id, @Nullable String displayName,
                         @Nullable String iconUrl) implements KBSerializable {

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = new JsonObject();

        obj.addProperty("id", this.id);
        obj.addProperty("displayName", this.displayName);
        obj.addProperty("iconUrl", this.iconUrl);

        return obj;
    }

}
