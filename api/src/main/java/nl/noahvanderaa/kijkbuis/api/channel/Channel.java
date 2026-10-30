package nl.noahvanderaa.kijkbuis.api.channel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A channel from a playlist.
 */
public interface Channel {

    /**
     * @return the channel's ID, which stays the same when its playlist is refreshed
     */
    @NotNull
    UUID id();

    /**
     * @return the playlist the channel comes from
     */
    @NotNull
    UUID playlistId();

    @NotNull
    String name();

    /**
     * @return the group the playlist puts the channel in
     */
    @Nullable
    String groupTitle();

    /**
     * @return the channel number from the playlist
     */
    @Nullable
    Integer channelNumber();

    /**
     * @return the ID of the EPG channel this channel gets its programmes from, if any
     */
    @Nullable
    String epgChannelId();

}
