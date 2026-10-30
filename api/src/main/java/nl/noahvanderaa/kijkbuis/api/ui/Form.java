package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A form people fill in and save, such as a plugin's settings. Saving sends a JSON object to the plugin's endpoint at
 * the form's action, with a value for each input under its name. What kind of value each input sends is described on
 * the input.
 * <p>
 * The values are checked in the browser (such as whether a required input is filled in), but the endpoint has to
 * check them again: anyone can send anything to it.
 */
public final class Form implements Component {

    private static final Pattern ACTION = Pattern.compile("[a-z0-9][a-z0-9-]*(?:/[a-z0-9][a-z0-9-]*)*");

    private final String action;
    private final List<Input> inputs = new ArrayList<>();
    private String submitLabel = "Save";
    private String successText = "Saved";

    /**
     * @param action the path of the plugin's endpoint that saves the form, such as settings. It's lowercase letters,
     *               digits and dashes, with slashes between parts.
     * @throws IllegalArgumentException if the action isn't such a path
     */
    public Form(@NotNull String action) {
        if (!ACTION.matcher(action).matches()) {
            throw new IllegalArgumentException("A form's action must be lowercase letters, digits and dashes, with slashes between parts, rather than " + action);
        }
        this.action = action;
    }

    /**
     * Adds an input below the others.
     *
     * @throws IllegalArgumentException if the form already has an input with the same name
     */
    @NotNull
    public Form add(@NotNull Input input) {
        if (this.inputs.stream().anyMatch((other) -> other.name().equals(input.name()))) {
            throw new IllegalArgumentException("The form already has an input named " + input.name());
        }
        this.inputs.add(input);
        return this;
    }

    /**
     * @param submitLabel the text on the button that saves the form, which is Save if not set
     */
    @NotNull
    public Form submitLabel(@NotNull String submitLabel) {
        this.submitLabel = submitLabel;
        return this;
    }

    /**
     * @param successText what the viewer sees once the form is saved, which is Saved if not set
     */
    @NotNull
    public Form successText(@NotNull String successText) {
        this.successText = successText;
        return this;
    }

    @NotNull
    public String action() {
        return this.action;
    }

    @NotNull
    public List<Input> inputs() {
        return List.copyOf(this.inputs);
    }

    @NotNull
    public String submitLabel() {
        return this.submitLabel;
    }

    @NotNull
    public String successText() {
        return this.successText;
    }

}
