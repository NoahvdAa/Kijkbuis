package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The days picked in a {@link DateRangeInput}, from the first to the last, both included. They're days as the viewer
 * sees them, so the range knows their time zone, and {@link #startsAt()} and {@link #endsAt()} give the moments they
 * mean.
 *
 * @param from the first day
 * @param to   the last day, which can be the same as the first
 * @param zone the time zone the days are in, such as the viewer's, see {@link FormValues#timeZone()}
 */
public record DateRange(@NotNull LocalDate from, @NotNull LocalDate to, @NotNull ZoneId zone) {

    /**
     * @throws IllegalArgumentException if the range ends before it starts
     */
    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(zone, "zone");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("A date range can't end (" + to + ") before it starts (" + from + ")");
        }
    }

    /**
     * @return when the first day starts
     */
    @NotNull
    public Instant startsAt() {
        return this.from.atStartOfDay(this.zone).toInstant();
    }

    /**
     * @return when the last day ends, which is when the day after it starts. That moment is no longer in the range.
     */
    @NotNull
    public Instant endsAt() {
        return this.to.plusDays(1).atStartOfDay(this.zone).toInstant();
    }

    /**
     * @return whether the moment falls on one of the days
     */
    public boolean contains(@NotNull Instant instant) {
        return !instant.isBefore(this.startsAt()) && instant.isBefore(this.endsAt());
    }

}
