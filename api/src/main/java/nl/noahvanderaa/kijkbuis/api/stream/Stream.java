package nl.noahvanderaa.kijkbuis.api.stream;

import nl.noahvanderaa.kijkbuis.api.channel.Channel;
import nl.noahvanderaa.kijkbuis.api.user.User;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone watching a channel. Every player has its own stream, so someone watching two channels at once has two.
 */
public interface Stream {

    @NotNull
    UUID id();

    /**
     * @return who's watching
     */
    @NotNull
    User owner();

    @NotNull
    Channel channel();

    /**
     * @return the quality the viewer picked: auto, or the ID of a transcoding profile
     */
    @NotNull
    String quality();

    /**
     * @return whether Kijkbuis transcodes the video, rather than passing it through as is
     */
    boolean transcoding();

    @NotNull
    Instant startedAt();

    /**
     * @return whether the stream is still going: it's true from its
     * {@link nl.noahvanderaa.kijkbuis.api.event.stream.StreamStartedEvent} until it's stopped, which is just before its
     * {@link nl.noahvanderaa.kijkbuis.api.event.stream.StreamStoppedEvent}. A stream whose channel cut out keeps
     * running until it's stopped for being idle.
     */
    boolean isRunning();

}
