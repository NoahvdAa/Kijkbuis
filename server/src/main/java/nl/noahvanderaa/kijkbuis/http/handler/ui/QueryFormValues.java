package nl.noahvanderaa.kijkbuis.http.handler.ui;

import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import nl.noahvanderaa.kijkbuis.api.ui.FormValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.util.Deque;
import java.util.Map;

/**
 * The values of a page's filters, from its URL's query, where everything is text. A date range has its days with two
 * dots in between, such as 2026-10-01..2026-10-30.
 */
final class QueryFormValues implements FormValues {

    private final Map<String, Deque<String>> query;
    private final ZoneId timeZone;

    QueryFormValues(@NotNull Map<String, Deque<String>> query, @NotNull ZoneId timeZone) {
        this.query = query;
        this.timeZone = timeZone;
    }

    @Override
    @Nullable
    public String string(@NotNull String name) {
        Deque<String> values = this.query.get(name);
        return values != null ? values.peekFirst() : null;
    }

    @Override
    @Nullable
    public Long number(@NotNull String name) {
        String value = this.string(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean checked(@NotNull String name) {
        return "true".equals(this.string(name));
    }

    @Override
    @Nullable
    public DateRange dateRange(@NotNull String name) {
        String value = this.string(name);
        if (value == null) {
            return null;
        }
        String[] days = value.split("\\.\\.", -1);
        return days.length == 2 ? DateRanges.parse(days[0], days[1], this.timeZone) : null;
    }

    @Override
    @NotNull
    public ZoneId timeZone() {
        return this.timeZone;
    }

}
