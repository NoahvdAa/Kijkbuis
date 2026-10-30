package nl.noahvanderaa.kijkbuis.api.event;

/**
 * When an event handler runs compared to the others for the same event. Lower priorities run first, so higher
 * priorities have the final say. Handlers with the same priority run in the order their listeners were registered.
 */
public enum EventPriority {
    /**
     * Runs first, so every other handler can change what it decided
     */
    LOWEST,
    LOW,
    /**
     * The default
     */
    NORMAL,
    HIGH,
    /**
     * Runs last of the handlers that may change the event, so it has the final say
     */
    HIGHEST,
    /**
     * Runs after everything else, to see what came of the event. Handlers with this priority must not change it.
     */
    MONITOR
}
