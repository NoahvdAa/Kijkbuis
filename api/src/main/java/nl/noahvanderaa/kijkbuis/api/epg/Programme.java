package nl.noahvanderaa.kijkbuis.api.epg;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A programme from the EPG. The image URLs come straight from the EPG, so browsers may not be able to reach them.
 */
public interface Programme {

    @NotNull
    Instant startsAt();

    @NotNull
    Instant endsAt();

    @NotNull
    String title();

    /**
     * @return the sub-title, such as the episode name
     */
    @Nullable
    String subtitle();

    @Nullable
    String description();

    @Nullable
    String category();

    /**
     * @return the URL of an image for the programme, which can be any shape
     */
    @Nullable
    String iconUrl();

    /**
     * @return the URL of a portrait poster
     */
    @Nullable
    String posterUrl();

    /**
     * @return the URL of a landscape background image, or else a still from the programme
     */
    @Nullable
    String backdropUrl();

}
