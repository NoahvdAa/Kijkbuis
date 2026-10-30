package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An input in a {@link Form}. Its value is sent under its name when the form is saved.
 */
public sealed interface Input permits TextInput, NumberInput, Checkbox, Select, DateRangeInput {

    @NotNull
    String name();

    @NotNull
    String label();

    /**
     * @return a hint below the input, such as what it's for
     */
    @Nullable
    String help();

}
