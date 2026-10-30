package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

/**
 * Where a plugin adds to Kijkbuis' pages, adds pages of its own, and saves the forms in them. They all stay until the
 * plugin is disabled.
 */
public interface UiManager {

    /**
     * Adds sections to a slot. Sections show up in the order plugins were enabled, then in the order their providers
     * were registered.
     */
    void register(@NotNull Slot slot, @NotNull SectionProvider provider);

    /**
     * Saves the forms that are sent to an action, such as settings for {@code new Form("settings")}.
     *
     * @param action the form's action, see {@link Form#Form(String)}
     * @throws IllegalArgumentException if the action isn't valid, or the plugin already handles it
     */
    void handleForm(@NotNull String action, @NotNull FormHandler handler);

    /**
     * Adds a page to the admin screens, with a link to it in their menu. Only admins can see it. Pages show up in the
     * order plugins were enabled, then in the order they were registered.
     *
     * @param id    the page's ID within the plugin, which ends up in its URL: lowercase letters, digits and dashes
     * @param title the name of the page in the menu, which should be short, such as Playback
     * @return the page, to add sub-pages to
     * @throws IllegalArgumentException if the ID isn't valid, or the plugin already has a page with it
     */
    @NotNull
    AdminPage registerPage(@NotNull String id, @NotNull String title, @NotNull PageProvider provider);

}
