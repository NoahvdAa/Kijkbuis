package nl.noahvanderaa.kijkbuis.http.handler.ui;

import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFormValuesTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private final QueryFormValues values = new QueryFormValues(query(
            "text", "Hello", "days", " 30 ", "lots", "lots", "yes", "true", "no", "false",
            "range", "2026-10-01..2026-10-30", "oneDay", "2026-10-30..2026-10-30", "backwards", "2026-10-30..2026-10-01",
            "halfRange", "2026-10-01..", "day", "2026-10-01", "threeDays", "2026-10-01..2026-10-02..2026-10-03", "notDays", "yesterday..today"
    ), AMSTERDAM);

    @Test
    void readsEverythingFromText() {
        assertEquals("Hello", this.values.string("text"));
        assertEquals(30L, this.values.number("days"));
        assertNull(this.values.number("lots"));
        assertTrue(this.values.checked("yes"));
        assertFalse(this.values.checked("no"));
        assertNull(this.values.string("missing"));
    }

    @Test
    void readsDateRangesInTheViewersTimeZone() {
        assertEquals(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), AMSTERDAM), this.values.dateRange("range"));
        assertEquals(new DateRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30), AMSTERDAM), this.values.dateRange("oneDay"));
        for (String name : List.of("backwards", "halfRange", "day", "threeDays", "notDays", "text", "missing")) {
            assertNull(this.values.dateRange(name), name);
        }
        assertEquals(AMSTERDAM, this.values.timeZone());
    }

    /**
     * @param params each name followed by its value
     */
    private static Map<String, Deque<String>> query(String... params) {
        Map<String, Deque<String>> query = new HashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            query.put(params[i], new ArrayDeque<>(List.of(params[i + 1])));
        }
        return query;
    }

}
