package nl.noahvanderaa.kijkbuis.api.event;

/**
 * Something that happens in Kijkbuis, which plugins can listen to with a {@link Listener}.
 * <p>
 * Events are called on the thread that caused them, which can be a different one every time. So handlers must be
 * thread-safe. They should also be quick, since whatever caused the event waits for them.
 */
public abstract class Event {

    protected Event() {
    }

}
