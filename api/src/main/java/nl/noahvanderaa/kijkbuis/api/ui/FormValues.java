package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;

/**
 * What was filled in on a form, by the names of its inputs. The values are what the browser sent, so they can be
 * missing or of another kind than the form asked for. Those come back as null (or false), the same as an empty input.
 */
public interface FormValues {

    /**
     * @return the text of a {@link TextInput}, or the picked option of a {@link Select}. Null if there's no text under
     * this name.
     */
    @Nullable
    String string(@NotNull String name);

    /**
     * @return the number of a {@link NumberInput}. Null if it was left empty, or there's no whole number under this
     * name.
     */
    @Nullable
    Long number(@NotNull String name);

    /**
     * @return whether a {@link Checkbox} is checked. False if there's no checkbox under this name.
     */
    boolean checked(@NotNull String name);

    /**
     * @return the days picked in a {@link DateRangeInput}, in the viewer's {@link #timeZone()}. Null if it was left
     * empty, or there's no range under this name.
     */
    @Nullable
    DateRange dateRange(@NotNull String name);

    /**
     * @return the time zone of the viewer, as their browser reports it, or the server's if it doesn't. It's what days
     * mean to them, such as today, or the ones in a {@link DateRange}.
     */
    @NotNull
    ZoneId timeZone();

}
