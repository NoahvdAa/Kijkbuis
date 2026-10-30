package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of figures with a label each, such as "Watched this week: 12 h".
 */
public final class Stats implements Component {

    private final List<Stat> stats = new ArrayList<>();

    /**
     * @param value the figure, formatted the way it should be shown
     */
    @NotNull
    public Stats add(@NotNull String label, @NotNull String value) {
        this.stats.add(new Stat(label, value));
        return this;
    }

    @NotNull
    public List<Stat> stats() {
        return List.copyOf(this.stats);
    }

    public record Stat(@NotNull String label, @NotNull String value) {
    }

}
