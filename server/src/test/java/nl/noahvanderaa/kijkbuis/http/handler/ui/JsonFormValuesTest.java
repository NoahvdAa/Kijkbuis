package nl.noahvanderaa.kijkbuis.http.handler.ui;

import com.google.gson.JsonParser;
import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFormValuesTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private final JsonFormValues values = new JsonFormValues(JsonParser.parseString("""
            {
                "text": "Hello", "empty": "", "nothing": null,
                "whole": 30, "wholeWithDecimals": 30.0, "fraction": 1.5, "huge": 99999999999999999999, "negative": -5,
                "yes": true, "no": false, "yesAsText": "true",
                "list": ["Hello"], "object": {"text": "Hello"},
                "range": {"from": "2026-10-01", "to": "2026-10-30"}, "oneDay": {"from": "2026-10-30", "to": "2026-10-30"},
                "backwards": {"from": "2026-10-30", "to": "2026-10-01"}, "halfRange": {"from": "2026-10-01", "to": null},
                "notDays": {"from": "yesterday", "to": "today"}, "rangeAsText": "2026-10-01..2026-10-30"
            }
            """).getAsJsonObject(), AMSTERDAM);

    @Test
    void readsStrings() {
        assertEquals("Hello", this.values.string("text"));
        assertEquals("", this.values.string("empty"));
        assertNull(this.values.string("nothing"));
        assertNull(this.values.string("whole"), "Numbers aren't text");
        assertNull(this.values.string("list"));
        assertNull(this.values.string("missing"));
    }

    @Test
    void readsWholeNumbers() {
        assertEquals(30L, this.values.number("whole"));
        assertEquals(30L, this.values.number("wholeWithDecimals"));
        assertEquals(-5L, this.values.number("negative"));
        assertNull(this.values.number("fraction"));
        assertNull(this.values.number("huge"), "Too large for a long");
        assertNull(this.values.number("nothing"));
        assertNull(this.values.number("text"));
        assertNull(this.values.number("missing"));
    }

    @Test
    void readsCheckboxes() {
        assertTrue(this.values.checked("yes"));
        assertFalse(this.values.checked("no"));
        assertFalse(this.values.checked("yesAsText"), "Only true itself counts");
        assertFalse(this.values.checked("object"));
        assertFalse(this.values.checked("missing"));
    }

    @Test
    void readsDateRangesInTheViewersTimeZone() {
        assertEquals(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), AMSTERDAM), this.values.dateRange("range"));
        assertEquals(new DateRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30), AMSTERDAM), this.values.dateRange("oneDay"));
        assertNull(this.values.dateRange("backwards"));
        assertNull(this.values.dateRange("halfRange"));
        assertNull(this.values.dateRange("notDays"));
        assertNull(this.values.dateRange("rangeAsText"), "Only the URLs of pages have ranges as text");
        assertNull(this.values.dateRange("object"));
        assertNull(this.values.dateRange("nothing"));
        assertNull(this.values.dateRange("missing"));
        assertEquals(AMSTERDAM, this.values.timeZone());
    }

}
