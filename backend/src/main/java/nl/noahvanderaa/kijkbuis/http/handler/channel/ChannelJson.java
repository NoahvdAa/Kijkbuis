package nl.noahvanderaa.kijkbuis.http.handler.channel;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.epg.EpgManager;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ChannelJson {

    private ChannelJson() {
    }

    /**
     * Serializes a channel along with what's on now and next.
     */
    @NotNull
    static JsonObject withNowNext(@NotNull Channel channel, @Nullable EpgManager.NowNext nowNext, @NotNull User user) {
        JsonObject obj = channel.serialize(user);
        obj.add("now", nowNext != null && nowNext.now() != null ? nowNext.now().serialize(user) : JsonNull.INSTANCE);
        obj.add("next", nowNext != null && nowNext.next() != null ? nowNext.next().serialize(user) : JsonNull.INSTANCE);
        return obj;
    }

}
