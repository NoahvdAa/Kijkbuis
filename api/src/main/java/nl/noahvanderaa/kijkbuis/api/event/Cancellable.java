package nl.noahvanderaa.kijkbuis.api.event;

/**
 * An event that's called before something happens, and stops it from happening when it's cancelled. Handlers that run
 * later still get the event, unless they {@link EventHandler#ignoreCancelled() ignore cancelled events}.
 */
public interface Cancellable {

    boolean isCancelled();

    /**
     * Cancels the event, or lets it go ahead after all if a handler that ran earlier cancelled it.
     */
    void setCancelled(boolean cancelled);

}
