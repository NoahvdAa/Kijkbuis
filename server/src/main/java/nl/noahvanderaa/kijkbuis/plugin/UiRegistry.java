package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.ui.Form;
import nl.noahvanderaa.kijkbuis.api.ui.FormHandler;
import nl.noahvanderaa.kijkbuis.api.ui.PageProvider;
import nl.noahvanderaa.kijkbuis.api.ui.Section;
import nl.noahvanderaa.kijkbuis.api.ui.SectionProvider;
import nl.noahvanderaa.kijkbuis.api.ui.Slot;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The sections plugins add to Kijkbuis' pages, the pages of their own, and what saves the forms in them. Sections and
 * pages are shown in the order they were registered, which follows the order the plugins were enabled in.
 */
public class UiRegistry {

    private static final Logger LOGGER = LogManager.getLogger(UiRegistry.class);
    private static final Pattern PAGE_ID = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final Object lock = new Object();
    /**
     * Changes replace the whole list, so pages can be put together without the lock
     */
    private volatile List<RegisteredProvider> providers = List.of();
    private volatile List<RegisteredPage> pages = List.of();
    private final Map<FormKey, RegisteredForm> forms = new ConcurrentHashMap<>();

    public void register(@NotNull PluginInfo plugin, @NotNull Slot slot, @NotNull SectionProvider provider) {
        synchronized (this.lock) {
            List<RegisteredProvider> providers = new ArrayList<>(this.providers);
            providers.add(new RegisteredProvider(plugin, slot, provider));
            this.providers = List.copyOf(providers);
        }
    }

    /**
     * @throws IllegalArgumentException if a form can't have the action, or the plugin already handles it
     */
    public void handleForm(@NotNull PluginInfo plugin, @NotNull String action, @NotNull FormHandler handler) {
        // Checks the action the same way forms do, and throws if it's not one a form can have
        new Form(action);
        if (this.forms.putIfAbsent(new FormKey(plugin.id(), action), new RegisteredForm(plugin, handler)) != null) {
            throw new IllegalArgumentException(plugin.name() + " already handles the forms sent to " + action);
        }
    }

    /**
     * @param parent the ID of the page this is a sub-page of, or null if it's a page of its own
     * @throws IllegalArgumentException if the ID isn't valid, or the plugin already has a page with it there
     */
    public void registerPage(@NotNull PluginInfo plugin, @Nullable String parent, @NotNull String id, @NotNull String title, @NotNull PageProvider provider) {
        if (!PAGE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("A page's ID must be lowercase letters, digits and dashes, rather than " + id);
        }
        synchronized (this.lock) {
            if (this.page(plugin.id(), parent, id) != null) {
                throw new IllegalArgumentException(plugin.name() + " already has a page at " + (parent != null ? parent + "/" : "") + id);
            }
            List<RegisteredPage> pages = new ArrayList<>(this.pages);
            pages.add(new RegisteredPage(plugin, parent, id, title, provider));
            this.pages = List.copyOf(pages);
        }
    }

    /**
     * Removes every section, page and form handler of a plugin, such as when it's disabled.
     */
    public void unregisterAll(@NotNull PluginInfo plugin) {
        synchronized (this.lock) {
            this.providers = this.providers.stream().filter((provider) -> !provider.plugin().id().equals(plugin.id())).toList();
            this.pages = this.pages.stream().filter((page) -> !page.plugin().id().equals(plugin.id())).toList();
        }
        this.forms.keySet().removeIf((key) -> key.pluginId().equals(plugin.id()));
    }

    /**
     * Asks every plugin what to show the viewer in a slot. Plugins that fail are logged and left out, so the page still
     * works.
     */
    @NotNull
    public List<PluginSection> sections(@NotNull Slot slot, @NotNull User viewer) {
        List<PluginSection> sections = new ArrayList<>();
        for (RegisteredProvider provider : this.providers) {
            if (provider.slot() != slot) {
                continue;
            }
            try {
                for (Section section : provider.provider().sections(viewer)) {
                    sections.add(new PluginSection(provider.plugin(), section));
                }
            } catch (Exception | LinkageError ex) {
                LOGGER.error("{} failed to put together its sections for {}", provider.plugin().name(), slot, ex);
            }
        }
        return sections;
    }

    /**
     * @param parent the ID of the page to get the sub-pages of, or null to get the plugin's own pages
     * @return the pages, in the order they were registered
     */
    @NotNull
    public List<RegisteredPage> pages(@NotNull String pluginId, @Nullable String parent) {
        return this.pages.stream().filter((page) -> page.plugin().id().equals(pluginId) && Objects.equals(page.parent(), parent)).toList();
    }

    /**
     * @param parent the ID of the page it's a sub-page of, or null for one of the plugin's own pages
     * @return the page, or null if there's none
     */
    @Nullable
    public RegisteredPage page(@NotNull String pluginId, @Nullable String parent, @NotNull String id) {
        return this.pages(pluginId, parent).stream().filter((page) -> page.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * @return what saves the forms sent to the plugin's action, or null if nothing does
     */
    @Nullable
    public RegisteredForm form(@NotNull String pluginId, @NotNull String action) {
        return this.forms.get(new FormKey(pluginId, action));
    }

    /**
     * @param plugin the plugin the section comes from
     */
    public record PluginSection(@NotNull PluginInfo plugin, @NotNull Section section) {
    }

    /**
     * @param plugin the plugin the page belongs to
     * @param parent the ID of the page it's a sub-page of, or null if it's a page of its own
     * @param id     the page's ID within the plugin, or within its parent
     * @param title  the name of the page in the menu
     */
    public record RegisteredPage(@NotNull PluginInfo plugin, @Nullable String parent, @NotNull String id,
                                 @NotNull String title,
                                 @NotNull PageProvider provider) {
    }

    /**
     * @param plugin the plugin that handles the form
     */
    public record RegisteredForm(@NotNull PluginInfo plugin, @NotNull FormHandler handler) {
    }

    private record RegisteredProvider(@NotNull PluginInfo plugin, @NotNull Slot slot,
                                      @NotNull SectionProvider provider) {
    }

    private record FormKey(@NotNull String pluginId, @NotNull String action) {
    }

}
