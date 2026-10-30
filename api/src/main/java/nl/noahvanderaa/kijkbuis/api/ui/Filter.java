package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Inputs that change what a page shows, such as how far back to look, or which user to look up. Applying them opens
 * the page again with what's filled in, which its {@link PageProvider} gets. What's filled in is part of the page's
 * URL, so a lookup can be bookmarked, and the back button goes to the one before.
 * <p>
 * Filters only work on a plugin's own pages, see {@link UiManager#registerPage}. Elsewhere, they're left out.
 */
public final class Filter implements Component {

    private final List<Input> inputs = new ArrayList<>();
    private String submitLabel = "Show";

    /**
     * Adds an input after the others.
     *
     * @throws IllegalArgumentException if the filter already has an input with the same name
     */
    @NotNull
    public Filter add(@NotNull Input input) {
        if (this.inputs.stream().anyMatch((other) -> other.name().equals(input.name()))) {
            throw new IllegalArgumentException("The filter already has an input named " + input.name());
        }
        this.inputs.add(input);
        return this;
    }

    /**
     * @param submitLabel the text on the button that applies the filter, which is Show if not set
     */
    @NotNull
    public Filter submitLabel(@NotNull String submitLabel) {
        this.submitLabel = submitLabel;
        return this;
    }

    @NotNull
    public List<Input> inputs() {
        return List.copyOf(this.inputs);
    }

    @NotNull
    public String submitLabel() {
        return this.submitLabel;
    }

}
