package nl.noahvanderaa.kijkbuis.api.event.stream;

import nl.noahvanderaa.kijkbuis.api.event.Event;
import nl.noahvanderaa.kijkbuis.api.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a stream stopped. Only streams that got a {@link StreamStartedEvent} get one, and always after that.
 */
public final class StreamStoppedEvent extends Event {

    private final Stream stream;
    private final Reason reason;

    public StreamStoppedEvent(@NotNull Stream stream, @NotNull Reason reason) {
        this.stream = stream;
        this.reason = reason;
    }

    @NotNull
    public Stream stream() {
        return this.stream;
    }

    @NotNull
    public Reason reason() {
        return this.reason;
    }

    public enum Reason {
        /**
         * The viewer stopped it, by closing the player, switching channel or quality, or logging out
         */
        VIEWER,
        /**
         * The player didn't ask for anything for a while, such as when the browser was closed
         */
        IDLE,
        /**
         * An admin stopped it, or signed the viewer out
         */
        ADMIN,
        /**
         * Kijkbuis is shutting down
         */
        SHUTDOWN
    }

}
