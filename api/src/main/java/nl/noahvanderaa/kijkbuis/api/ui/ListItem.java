package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * An item in an {@link ItemList}: a title, with optional details around it.
 */
public final class ListItem {

    private final String title;
    private String subtitle = null;
    private String detail = null;
    private Instant time = null;
    private String imageUrl = null;

    public ListItem(@NotNull String title) {
        this.title = title;
    }

    /**
     * @param subtitle a line below the title, such as the channel a programme was on
     */
    @NotNull
    public ListItem subtitle(@Nullable String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    /**
     * @param detail a short text at the end of the item, such as how long something took
     */
    @NotNull
    public ListItem detail(@Nullable String detail) {
        this.detail = detail;
        return this;
    }

    /**
     * @param time when it happened, which viewers see in their own time zone
     */
    @NotNull
    public ListItem time(@Nullable Instant time) {
        this.time = time;
        return this;
    }

    /**
     * @param imageUrl the http(s) URL of an image, which is shown as a 16:9 thumbnail. Other URLs are ignored.
     */
    @NotNull
    public ListItem imageUrl(@Nullable String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    @NotNull
    public String title() {
        return this.title;
    }

    @Nullable
    public String subtitle() {
        return this.subtitle;
    }

    @Nullable
    public String detail() {
        return this.detail;
    }

    @Nullable
    public Instant time() {
        return this.time;
    }

    @Nullable
    public String imageUrl() {
        return this.imageUrl;
    }

}
