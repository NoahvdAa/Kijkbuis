package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A table of rows with the same columns, such as how much each user watched. Each cell is one of:
 * <ul>
 *     <li>a {@link String}, shown as is</li>
 *     <li>a {@link Number}, shown in the viewer's own format, such as 1,234. Columns of numbers line up on the right.</li>
 *     <li>an {@link Instant}, shown as a date and time in the viewer's own time zone</li>
 *     <li>null, for an empty cell</li>
 * </ul>
 */
public final class Table implements Component {

    private final List<String> columns;
    private final List<List<Object>> rows = new ArrayList<>();
    private String emptyText = null;

    /**
     * @param columns the headings of the columns
     * @throws IllegalArgumentException if there are no columns
     */
    public Table(@NotNull String... columns) {
        if (columns.length == 0) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        this.columns = List.of(columns);
    }

    /**
     * Adds a row below the others.
     *
     * @param cells a cell for each column, in the same order
     * @throws IllegalArgumentException if there are more or fewer cells than columns, or a cell is of another kind than
     *                                  a table can show
     */
    @NotNull
    public Table row(@Nullable Object... cells) {
        if (cells.length != this.columns.size()) {
            throw new IllegalArgumentException("The table has " + this.columns.size() + " columns, but the row has " + cells.length + " cells");
        }
        for (Object cell : cells) {
            if (cell != null && !(cell instanceof String) && !(cell instanceof Number) && !(cell instanceof Instant)) {
                throw new IllegalArgumentException("A table can't show a " + cell.getClass().getName() + ", only text, numbers and instants");
            }
        }
        // Arrays.asList, since List.of doesn't take nulls
        this.rows.add(Collections.unmodifiableList(Arrays.asList(cells.clone())));
        return this;
    }

    /**
     * @param emptyText what to show when the table has no rows, such as "Nobody watched anything yet", rather than a
     *                  general message
     */
    @NotNull
    public Table emptyText(@Nullable String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    @NotNull
    public List<String> columns() {
        return this.columns;
    }

    /**
     * @return the rows, with a cell for each column. Cells can be null.
     */
    @NotNull
    public List<List<Object>> rows() {
        return List.copyOf(this.rows);
    }

    @Nullable
    public String emptyText() {
        return this.emptyText;
    }

}
