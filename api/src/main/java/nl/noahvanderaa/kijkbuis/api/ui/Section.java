package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A part of a page that a plugin adds: a title, and the components under it.
 */
public final class Section {

    private final String title;
    private final List<Component> components = new ArrayList<>();

    public Section(@NotNull String title, @NotNull Component... components) {
        this.title = title;
        this.components.addAll(List.of(components));
    }

    /**
     * Adds a component below the others.
     */
    @NotNull
    public Section add(@NotNull Component component) {
        this.components.add(component);
        return this;
    }

    @NotNull
    public String title() {
        return this.title;
    }

    @NotNull
    public List<Component> components() {
        return List.copyOf(this.components);
    }

}
