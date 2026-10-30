package nl.noahvanderaa.kijkbuis.epg;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A programme from the EPG.
 *
 * @param startsAt    when the programme starts
 * @param endsAt      when the programme ends
 * @param title       the title
 * @param subtitle    the sub-title, such as the episode name
 * @param description the description
 * @param category    the category
 * @param iconUrl     the URL of an image for the programme, which can be any shape
 * @param posterUrl   the URL of a portrait poster
 * @param backdropUrl the URL of a landscape background image
 */
public record Programme(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull String title,
        @Nullable String subtitle,
        @Nullable String description,
        @Nullable String category,
        @Nullable String iconUrl,
        @Nullable String posterUrl,
        @Nullable String backdropUrl
) implements nl.noahvanderaa.kijkbuis.api.epg.Programme, KBSerializable {

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = new JsonObject();

        obj.addProperty("startsAt", this.startsAt.toString());
        obj.addProperty("endsAt", this.endsAt.toString());
        obj.addProperty("title", this.title);
        obj.addProperty("subtitle", this.subtitle);
        obj.addProperty("description", this.description);
        obj.addProperty("category", this.category);
        obj.addProperty("iconUrl", this.iconUrl);
        obj.addProperty("posterUrl", this.posterUrl);
        obj.addProperty("backdropUrl", this.backdropUrl);

        return obj;
    }

}
