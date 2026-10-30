package nl.noahvanderaa.kijkbuis.api.event.stream;

import nl.noahvanderaa.kijkbuis.api.channel.Channel;
import nl.noahvanderaa.kijkbuis.api.event.Cancellable;
import nl.noahvanderaa.kijkbuis.api.event.Event;
import nl.noahvanderaa.kijkbuis.api.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Called when someone wants to watch a channel, before the stream starts. Cancelling it stops the stream from starting,
 * and the viewer sees why.
 * <p>
 * The stream can still fail to start after this, such as when the channel is down. Streams that do start get a
 * {@link StreamStartedEvent}.
 */
public final class StreamStartingEvent extends Event implements Cancellable {

    private static final String DEFAULT_CANCEL_MESSAGE = "You can't watch this channel right now";

    private final User user;
    private final Channel channel;
    private final String quality;
    private boolean cancelled = false;
    private String cancelMessage = DEFAULT_CANCEL_MESSAGE;

    public StreamStartingEvent(@NotNull User user, @NotNull Channel channel, @NotNull String quality) {
        this.user = user;
        this.channel = channel;
        this.quality = quality;
    }

    /**
     * @return who wants to watch
     */
    @NotNull
    public User user() {
        return this.user;
    }

    @NotNull
    public Channel channel() {
        return this.channel;
    }

    /**
     * @return the quality they picked: auto, or the ID of a transcoding profile
     */
    @NotNull
    public String quality() {
        return this.quality;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Cancels the event, and tells the viewer why.
     *
     * @param message what the viewer sees, such as "You can only watch two channels at once"
     */
    public void cancel(@NotNull String message) {
        this.cancelled = true;
        this.cancelMessage = message;
    }

    /**
     * @return what the viewer sees if the event is cancelled
     */
    @NotNull
    public String cancelMessage() {
        return this.cancelMessage;
    }

}
