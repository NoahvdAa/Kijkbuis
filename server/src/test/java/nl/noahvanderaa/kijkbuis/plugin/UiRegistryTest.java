package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.ui.FormHandler;
import nl.noahvanderaa.kijkbuis.api.ui.PageProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiRegistryTest {

    private static final PluginInfo PLUGIN = new PluginInfo("plugin", "Plugin", "1.0.0", "Plugin", "0.0", null, null);
    private static final PluginInfo OTHER_PLUGIN = new PluginInfo("other", "Other plugin", "1.0.0", "Other", "0.0", null, null);
    private static final FormHandler HANDLER = (viewer, values) -> {
    };

    private static final PageProvider PAGE = (viewer, filters) -> List.of();

    private final UiRegistry registry = new UiRegistry();

    @Test
    void subPagesBelongToTheirPage() {
        this.registry.registerPage(PLUGIN, null, "playback", "Playback", PAGE);
        this.registry.registerPage(PLUGIN, null, "users", "Users", PAGE);
        this.registry.registerPage(PLUGIN, "playback", "users", "Users", PAGE);

        assertEquals(List.of("playback", "users"), this.registry.pages("plugin", null).stream().map(UiRegistry.RegisteredPage::id).toList(),
                "Sub-pages aren't pages of their own");
        assertEquals(List.of("users"), this.registry.pages("plugin", "playback").stream().map(UiRegistry.RegisteredPage::id).toList());
        assertNotNull(this.registry.page("plugin", "playback", "users"));
        assertNull(this.registry.page("plugin", "users", "users"));
        assertThrows(IllegalArgumentException.class, () -> this.registry.registerPage(PLUGIN, "playback", "users", "Users", PAGE), "One per page");
        assertThrows(IllegalArgumentException.class, () -> this.registry.registerPage(PLUGIN, "playback", "Not valid", "Users", PAGE));

        this.registry.unregisterAll(PLUGIN);
        assertEquals(List.of(), this.registry.pages("plugin", "playback"));
    }

    @Test
    void formsAreHandledPerPluginAndAction() {
        this.registry.handleForm(PLUGIN, "settings", HANDLER);
        this.registry.handleForm(OTHER_PLUGIN, "settings", HANDLER);

        assertNotNull(this.registry.form("plugin", "settings"));
        assertNull(this.registry.form("plugin", "other"));
        assertThrows(IllegalArgumentException.class, () -> this.registry.handleForm(PLUGIN, "settings", HANDLER), "One handler per action");

        this.registry.unregisterAll(PLUGIN);
        assertNull(this.registry.form("plugin", "settings"));
        assertNotNull(this.registry.form("other", "settings"));
    }

    @Test
    void onlyHandlesActionsFormsCanHave() {
        assertThrows(IllegalArgumentException.class, () -> this.registry.handleForm(PLUGIN, "/settings", HANDLER));
        assertThrows(IllegalArgumentException.class, () -> this.registry.handleForm(PLUGIN, "../settings", HANDLER));
    }

}
