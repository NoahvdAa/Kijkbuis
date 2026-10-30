package nl.noahvanderaa.kijkbuis.api.epg;

import nl.noahvanderaa.kijkbuis.api.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The programmes from the EPG. Kijkbuis keeps them for up to two weeks ahead, and for 12 hours after they ended.
 */
public interface Guide {

    /**
     * Finds the programmes airing on a channel at some point in a time window.
     *
     * @return the programmes in order, which is empty if the channel has no EPG
     * @throws IllegalStateException if the EPG can't be read
     */
    @NotNull
    List<Programme> programmes(@NotNull Channel channel, @NotNull Instant from, @NotNull Instant to);

    /**
     * @return the programme that's on the channel right now, or null if nothing is or the channel has no EPG
     * @throws IllegalStateException if the EPG can't be read
     */
    @Nullable
    Programme now(@NotNull Channel channel);

    /**
     * @return the first programme that starts on the channel after now, within the next 12 hours. Null if there's none,
     * or the channel has no EPG.
     * @throws IllegalStateException if the EPG can't be read
     */
    @Nullable
    Programme next(@NotNull Channel channel);

}
