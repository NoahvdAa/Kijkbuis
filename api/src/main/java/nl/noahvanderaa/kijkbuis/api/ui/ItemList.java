package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of items, such as the programmes someone watched.
 */
public final class ItemList implements Component {

    private final List<ListItem> items = new ArrayList<>();
    private String emptyText = null;

    /**
     * Adds an item to the end of the list.
     */
    @NotNull
    public ItemList add(@NotNull ListItem item) {
        this.items.add(item);
        return this;
    }

    /**
     * @param emptyText what to show when the list has no items, such as "You haven't watched anything yet", rather
     *                  than a general message
     */
    @NotNull
    public ItemList emptyText(@Nullable String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    @NotNull
    public List<ListItem> items() {
        return List.copyOf(this.items);
    }

    @Nullable
    public String emptyText() {
        return this.emptyText;
    }

}
