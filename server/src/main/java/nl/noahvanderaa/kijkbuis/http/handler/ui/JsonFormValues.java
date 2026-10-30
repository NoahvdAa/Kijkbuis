package nl.noahvanderaa.kijkbuis.http.handler.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import nl.noahvanderaa.kijkbuis.api.ui.FormValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;

/**
 * The values of a form as the frontend sends them: a JSON object with a value for each input. A date range is an
 * object with the days in it, such as {"from": "2026-10-01", "to": "2026-10-30"}.
 */
final class JsonFormValues implements FormValues {

    private final JsonObject json;
    private final ZoneId timeZone;

    JsonFormValues(@NotNull JsonObject json, @NotNull ZoneId timeZone) {
        this.json = json;
        this.timeZone = timeZone;
    }

    @Override
    @Nullable
    public String string(@NotNull String name) {
        JsonPrimitive value = this.primitive(name);
        return value != null && value.isString() ? value.getAsString() : null;
    }

    @Override
    @Nullable
    public Long number(@NotNull String name) {
        JsonPrimitive value = this.primitive(name);
        if (value == null || !value.isNumber()) {
            return null;
        }
        try {
            // Fails for fractions and numbers too large for a long
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean checked(@NotNull String name) {
        JsonPrimitive value = this.primitive(name);
        return value != null && value.isBoolean() && value.getAsBoolean();
    }

    @Override
    @Nullable
    public DateRange dateRange(@NotNull String name) {
        JsonElement value = this.json.get(name);
        if (value == null || !value.isJsonObject()) {
            return null;
        }
        JsonObject range = value.getAsJsonObject();
        return DateRanges.parse(text(range.get("from")), text(range.get("to")), this.timeZone);
    }

    @Override
    @NotNull
    public ZoneId timeZone() {
        return this.timeZone;
    }

    @Nullable
    private JsonPrimitive primitive(@NotNull String name) {
        JsonElement value = this.json.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
    }

    @Nullable
    private static String text(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

}
