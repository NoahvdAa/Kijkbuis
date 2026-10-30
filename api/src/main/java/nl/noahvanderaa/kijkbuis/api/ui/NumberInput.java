package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An input for a whole number. Its value is sent as a number, or null if it's left empty.
 */
public final class NumberInput implements Input {

    private final String name;
    private final String label;
    private String help = null;
    private Long value = null;
    private Long min = null;
    private Long max = null;
    private String unit = null;
    private boolean required = false;

    public NumberInput(@NotNull String name, @NotNull String label) {
        this.name = name;
        this.label = label;
    }

    @NotNull
    public NumberInput help(@Nullable String help) {
        this.help = help;
        return this;
    }

    /**
     * @param value the number the input starts with, such as the current setting
     */
    @NotNull
    public NumberInput value(long value) {
        this.value = value;
        return this;
    }

    @NotNull
    public NumberInput min(long min) {
        this.min = min;
        return this;
    }

    @NotNull
    public NumberInput max(long max) {
        this.max = max;
        return this;
    }

    /**
     * @param unit what the number counts, shown after the input, such as days
     */
    @NotNull
    public NumberInput unit(@Nullable String unit) {
        this.unit = unit;
        return this;
    }

    /**
     * @param required whether the form can only be saved with a number filled in
     */
    @NotNull
    public NumberInput required(boolean required) {
        this.required = required;
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

    @Nullable
    public Long value() {
        return this.value;
    }

    @Nullable
    public Long min() {
        return this.min;
    }

    @Nullable
    public Long max() {
        return this.max;
    }

    @Nullable
    public String unit() {
        return this.unit;
    }

    public boolean required() {
        return this.required;
    }

}
