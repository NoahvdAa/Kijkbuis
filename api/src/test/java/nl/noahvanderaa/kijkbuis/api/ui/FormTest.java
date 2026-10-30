package nl.noahvanderaa.kijkbuis.api.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormTest {

    @Test
    void actionsArePathsWithinThePlugin() {
        for (String action : List.of("settings", "history/clear", "a-1/b-2")) {
            assertDoesNotThrow(() -> new Form(action), action);
        }
        for (String action : List.of("", "/settings", "settings/", "history//clear", "../settings", "Settings", "settings?x=1", "https://example.com")) {
            assertThrows(IllegalArgumentException.class, () -> new Form(action), action);
        }
    }

    @Test
    void inputNamesAreUnique() {
        Form form = new Form("settings").add(new TextInput("name", "Name"));
        assertThrows(IllegalArgumentException.class, () -> form.add(new Checkbox("name", "Also name")));
        assertEquals(List.of("name"), form.inputs().stream().map(Input::name).toList());
    }

}
