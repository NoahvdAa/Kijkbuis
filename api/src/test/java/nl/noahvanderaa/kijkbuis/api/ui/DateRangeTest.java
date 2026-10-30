package nl.noahvanderaa.kijkbuis.api.ui;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    @Test
    void spansWholeDaysInItsTimeZone() {
        DateRange range = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), AMSTERDAM);
        assertEquals(Instant.parse("2026-09-30T22:00:00Z"), range.startsAt());
        assertEquals(Instant.parse("2026-10-07T22:00:00Z"), range.endsAt());

        assertTrue(range.contains(range.startsAt()));
        assertTrue(range.contains(range.endsAt().minusNanos(1)));
        assertFalse(range.contains(range.endsAt()), "The end is when the next day starts");
        assertFalse(range.contains(range.startsAt().minusNanos(1)));
    }

    @Test
    void daysCanBeLongerOrShorter() {
        // The clocks go back an hour that night
        DateRange range = new DateRange(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), AMSTERDAM);
        assertEquals(Duration.ofHours(25), Duration.between(range.startsAt(), range.endsAt()));
    }

    @Test
    void rangesCantEndBeforeTheyStart() {
        assertThrows(IllegalArgumentException.class, () -> new DateRange(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 1), AMSTERDAM));
        assertThrows(IllegalArgumentException.class, () -> new DateRangeInput("period", "Period").value(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 1)));
    }

    @Test
    void inputsStartWithTheDaysOfARange() {
        DateRangeInput input = new DateRangeInput("period", "Period").value(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), AMSTERDAM));
        assertEquals(LocalDate.of(2026, 10, 1), input.from());
        assertEquals(LocalDate.of(2026, 10, 7), input.to());

        input.value(null);
        assertNull(input.from());
        assertNull(input.to());
    }

}
