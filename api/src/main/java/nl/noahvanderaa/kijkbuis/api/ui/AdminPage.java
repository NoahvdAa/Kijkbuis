package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

/**
 * A page a plugin added to the admin screens, see {@link UiManager#registerPage}.
 */
public interface AdminPage {

    /**
     * Adds a sub-page to this page, such as one for each part of a report. Its link sits right below this page's in the
     * admin menu, and only shows while this page or one of its sub-pages is open. Sub-pages can't have sub-pages of
     * their own.
     *
     * @param id    the sub-page's ID within this page, which ends up in its URL: lowercase letters, digits and dashes
     * @param title the name of the sub-page in the menu
     * @return this page, to add more sub-pages
     * @throws IllegalArgumentException if the ID isn't valid, or this page already has a sub-page with it
     */
    @NotNull
    AdminPage subPage(@NotNull String id, @NotNull String title, @NotNull PageProvider provider);

}
