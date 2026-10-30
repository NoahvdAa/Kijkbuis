package nl.noahvanderaa.kijkbuis.api.event;

/**
 * A class with event handlers: methods annotated with {@link EventHandler} that take a single {@link Event}. A handler
 * gets every event of the type it takes, including subclasses. Register listeners with {@link EventManager#register}.
 */
public interface Listener {
}
