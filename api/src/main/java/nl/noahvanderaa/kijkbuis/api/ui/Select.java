package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An input to pick one of several options. The value of the picked option is sent as a string.
 */
public final class Select implements Input {

    private final String name;
    private final String label;
    private final List<Option> options = new ArrayList<>();
    private String help = null;
    private String value = null;

    public Select(@NotNull String name, @NotNull String label) {
        this.name = name;
        this.label = label;
    }

    @NotNull
    public Select help(@Nullable String help) {
        this.help = help;
        return this;
    }

    /**
     * Adds an option below the others.
     *
     * @param value what's sent when it's picked
     * @param label what the viewer sees
     */
    @NotNull
    public Select option(@NotNull String value, @NotNull String label) {
        this.options.add(new Option(value, label));
        return this;
    }

    /**
     * @param value the value of the option that's picked at first, such as the current setting. If it's not set, or
     *              not one of the options, the first option is picked.
     */
    @NotNull
    public Select value(@Nullable String value) {
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

    @NotNull
    public List<Option> options() {
        return List.copyOf(this.options);
    }

    @Nullable
    public String value() {
        return this.value;
    }

    public record Option(@NotNull String value, @NotNull String label) {
    }

}
