package nl.noahvanderaa.kijkbuis.api.event;

import org.jetbrains.annotations.NotNull;

/**
 * Where a plugin registers its listeners. They stay registered until they're unregistered, or the plugin is disabled.
 */
public interface EventManager {

    /**
     * Registers every event handler in a listener.
     *
     * @throws IllegalArgumentException if one of them doesn't take a single event
     */
    void register(@NotNull Listener listener);

    /**
     * Unregisters every event handler in a listener.
     */
    void unregister(@NotNull Listener listener);

}
