package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

/**
 * A paragraph of text. Line breaks in it are kept.
 */
public final class Text implements Component {

    private final String text;

    public Text(@NotNull String text) {
        this.text = text;
    }

    @NotNull
    public String text() {
        return this.text;
    }

}
