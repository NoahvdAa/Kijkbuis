package nl.noahvanderaa.kijkbuis.api.event.stream;

import nl.noahvanderaa.kijkbuis.api.event.Event;
import nl.noahvanderaa.kijkbuis.api.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Called once a stream started: its first segment is ready, and the viewer's player is about to start playing it.
 * Every stream that gets this gets a {@link StreamStoppedEvent} later, and never before this one.
 */
public final class StreamStartedEvent extends Event {

    private final Stream stream;

    public StreamStartedEvent(@NotNull Stream stream) {
        this.stream = stream;
    }

    @NotNull
    public Stream stream() {
        return this.stream;
    }

}
