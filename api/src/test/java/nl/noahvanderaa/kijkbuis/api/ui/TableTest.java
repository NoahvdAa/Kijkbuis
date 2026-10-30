package nl.noahvanderaa.kijkbuis.api.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableTest {

    @Test
    void rowsHaveACellForEachColumn() {
        Table table = new Table("User", "Plays");
        assertThrows(IllegalArgumentException.class, () -> table.row("Noah"));
        assertThrows(IllegalArgumentException.class, () -> table.row("Noah", 12, "Too many"));
        assertEquals(List.of(), table.rows());
    }

    @Test
    void cellsAreTextNumbersInstantsOrEmpty() {
        Instant now = Instant.now();
        Table table = new Table("User", "Plays", "Hours", "Last watched").row("Noah", 12, 3.5, now).row(null, null, null, null);
        assertEquals(List.of(List.of("Noah", 12, 3.5, now), Arrays.asList(null, null, null, null)), table.rows());

        assertThrows(IllegalArgumentException.class, () -> table.row("Noah", 12, 3.5, List.of("Not a cell")));
    }

    @Test
    void tablesNeedAColumn() {
        assertThrows(IllegalArgumentException.class, Table::new);
    }

}
