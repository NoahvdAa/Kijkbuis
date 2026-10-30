package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;

/**
 * An input to pick a range of days, such as which days to look up. It's read with {@link FormValues#dateRange}, and
 * either has both days or is empty.
 */
public final class DateRangeInput implements Input {

    private final String name;
    private final String label;
    private String help = null;
    private LocalDate from = null;
    private LocalDate to = null;
    private boolean required = false;

    public DateRangeInput(@NotNull String name, @NotNull String label) {
        this.name = name;
        this.label = label;
    }

    @NotNull
    public DateRangeInput help(@Nullable String help) {
        this.help = help;
        return this;
    }

    /**
     * @param from the first day the input starts with, such as from what's filled in now
     * @param to   the last day it starts with
     * @throws IllegalArgumentException if the last day is before the first
     */
    @NotNull
    public DateRangeInput value(@NotNull LocalDate from, @NotNull LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("A date range can't end (" + to + ") before it starts (" + from + ")");
        }
        this.from = from;
        this.to = to;
        return this;
    }

    /**
     * @param range the days the input starts with, such as what's filled in now, or null to start empty. Its time zone
     *              doesn't matter here.
     */
    @NotNull
    public DateRangeInput value(@Nullable DateRange range) {
        this.from = range != null ? range.from() : null;
        this.to = range != null ? range.to() : null;
        return this;
    }

    /**
     * @param required whether the form can only be saved with both days picked
     */
    @NotNull
    public DateRangeInput required(boolean required) {
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

    /**
     * @return the first day the input starts with, or null if it starts empty
     */
    @Nullable
    public LocalDate from() {
        return this.from;
    }

    /**
     * @return the last day the input starts with, or null if it starts empty
     */
    @Nullable
    public LocalDate to() {
        return this.to;
    }

    public boolean required() {
        return this.required;
    }

}
