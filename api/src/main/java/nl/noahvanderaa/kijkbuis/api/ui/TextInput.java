package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An input for text. Its value is sent as a string, which is empty if it's left empty.
 */
public final class TextInput implements Input {

    private final String name;
    private final String label;
    private String help = null;
    private String value = "";
    private String placeholder = null;
    private boolean required = false;
    private Integer maxLength = null;
    private boolean multiline = false;

    public TextInput(@NotNull String name, @NotNull String label) {
        this.name = name;
        this.label = label;
    }

    @NotNull
    public TextInput help(@Nullable String help) {
        this.help = help;
        return this;
    }

    /**
     * @param value the text the input starts with, such as the current setting
     */
    @NotNull
    public TextInput value(@NotNull String value) {
        this.value = value;
        return this;
    }

    /**
     * @param placeholder an example shown in the input while it's empty
     */
    @NotNull
    public TextInput placeholder(@Nullable String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * @param required whether the form can only be saved with something filled in
     */
    @NotNull
    public TextInput required(boolean required) {
        this.required = required;
        return this;
    }

    /**
     * @param maxLength how many characters can be filled in at most
     */
    @NotNull
    public TextInput maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /**
     * @param multiline whether the input takes several lines of text, rather than one
     */
    @NotNull
    public TextInput multiline(boolean multiline) {
        this.multiline = multiline;
        return this;
    }

    @Override
    @NotNull
    public String name() {
        return this.name;
    }

    @Override
    @NotNull
    public String label() {
        return this.label;
    }

    @Override
    @Nullable
    public String help() {
        return this.help;
    }

    @NotNull
    public String value() {
        return this.value;
    }

    @Nullable
    public String placeholder() {
        return this.placeholder;
    }

    public boolean required() {
        return this.required;
    }

    @Nullable
    public Integer maxLength() {
        return this.maxLength;
    }

    public boolean multiline() {
        return this.multiline;
    }

}
