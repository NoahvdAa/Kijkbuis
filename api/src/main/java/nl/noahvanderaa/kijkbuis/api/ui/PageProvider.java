package nl.noahvanderaa.kijkbuis.api.ui;

import nl.noahvanderaa.kijkbuis.api.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Decides what a plugin's page shows, every time an admin opens it or applies its {@link Filter}. It's called on the
 * thread of the request, so it must be thread-safe, and should be quick.
 */
@FunctionalInterface
public interface PageProvider {

    /**
     * @param viewer  the admin who opened the page
     * @param filters what's filled in in the page's filters. When the page is first opened, nothing is, so pick the
     *                defaults then, and show them in the filter.
     * @return the sections to show. If this throws, the error is logged, and the admin sees the page couldn't be shown.
     */
    @NotNull
    List<Section> sections(@NotNull User viewer, @NotNull FormValues filters) throws Exception;

}
