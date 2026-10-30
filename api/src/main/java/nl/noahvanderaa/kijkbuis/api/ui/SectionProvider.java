package nl.noahvanderaa.kijkbuis.api.ui;

import nl.noahvanderaa.kijkbuis.api.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Decides what a plugin shows in a slot, every time someone opens the page. It's called on the thread of the request,
 * so it must be thread-safe, and should be quick.
 */
@FunctionalInterface
public interface SectionProvider {

    /**
     * @param viewer who opened the page
     * @return the sections to show them, which can be none. If this throws, the error is logged and the page is shown
     * without these sections.
     */
    @NotNull
    List<Section> sections(@NotNull User viewer) throws Exception;

}
