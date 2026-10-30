package nl.noahvanderaa.kijkbuis.http.handler.ui;

import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Reads the days of a {@link nl.noahvanderaa.kijkbuis.api.ui.DateRangeInput} as the frontend sends them, such as
 * 2026-10-30.
 */
final class DateRanges {

    private DateRanges() {
    }

    /**
     * @return the range, or null if a day is missing or isn't one, or the range ends before it starts
     */
    @Nullable
    static DateRange parse(@Nullable String from, @Nullable String to, @NotNull ZoneId zone) {
        if (from == null || to == null) {
            return null;
        }
        try {
            LocalDate first = LocalDate.parse(from);
            LocalDate last = LocalDate.parse(to);
            return !last.isBefore(first) ? new DateRange(first, last, zone) : null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

}
