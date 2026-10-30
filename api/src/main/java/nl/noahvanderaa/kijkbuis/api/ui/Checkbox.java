package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A checkbox, with its label next to it. Its value is sent as true or false.
 */
public final class Checkbox implements Input {

    private final String name;
    private final String label;
    private String help = null;
    private boolean value = false;

    public Checkbox(@NotNull String name, @NotNull String label) {
        this.name = name;
        this.label = label;
    }

    @NotNull
    public Checkbox help(@Nullable String help) {
        this.help = help;
        return this;
    }

    /**
     * @param value whether it starts checked, such as the current setting
     */
    @NotNull
    public Checkbox value(boolean value) {
        this.value = value;
        return this;
    }

    @Override
    @NotNull
    public String name() {
        return this.name;
    }

    @Override
    @NotNull
    public String label() {
        return this.label;
    }

    @Override
    @Nullable
    public String help() {
        return this.help;
    }

    public boolean value() {
        return this.value;
    }

}
