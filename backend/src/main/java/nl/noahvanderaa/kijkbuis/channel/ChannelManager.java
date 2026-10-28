package nl.noahvanderaa.kijkbuis.channel;

import nl.noahvanderaa.kijkbuis.database.Database;
import nl.noahvanderaa.kijkbuis.playlist.m3u.M3UEntry;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChannelManager {

    private static final String SELECT_CHANNELS = "SELECT c.id, c.playlist_id, c.name, c.group_title, c.logo_url, c.stream_url, c.tvg_id, c.epg_channel_override, c.channel_number " +
            "FROM channels c JOIN playlists p ON p.id = c.playlist_id ";

    private final Database database;

    public ChannelManager(@NotNull Database database) {
        this.database = database;
    }

    /**
     * Lists the channels a user may watch, in playlist order.
     *
     * @param user       the user
     * @param playlistId only list the channels of this playlist, or null for all channels
     */
    @NotNull
    public List<Channel> channelsVisibleTo(@NotNull User user, @Nullable UUID playlistId) throws SQLException {
        // Every user can see every playlist for now, this is where group-based visibility goes
        String sql = SELECT_CHANNELS + (playlistId != null ? "WHERE c.playlist_id = ? " : "") + "ORDER BY p.created_at, p.id, c.position";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (playlistId != null) {
                statement.setString(1, playlistId.toString());
            }

            List<Channel> channels = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    channels.add(readChannel(resultSet));
                }
            }
            return channels;
        }
    }

    /**
     * @return the channel, or null if it doesn't exist or the user may not watch it
     */
    @Nullable
    public Channel findVisibleTo(@NotNull User user, @NotNull UUID id) throws SQLException {
        // Every user can see every playlist for now, this is where group-based visibility goes
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CHANNELS + "WHERE c.id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readChannel(resultSet) : null;
            }
        }
    }

    /**
     * Maps a channel to a different EPG channel than its tvg-id says. The mapping survives playlist refreshes.
     *
     * @param epgChannelId the EPG channel ID, or null to use the tvg-id again
     * @return the updated channel, or null if it doesn't exist
     */
    @Nullable
    public Channel setEpgChannelOverride(@NotNull UUID id, @Nullable String epgChannelId) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement("UPDATE channels SET epg_channel_override = ? WHERE id = ?")) {
            statement.setString(1, epgChannelId);
            statement.setString(2, id.toString());
            if (statement.executeUpdate() == 0) {
                return null;
            }
        }

        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CHANNELS + "WHERE c.id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readChannel(resultSet) : null;
            }
        }
    }

    /**
     * Replaces a playlist's channels with the given entries.
     * Entries that match an existing channel (see {@link #identify}) update that channel, so it keeps its ID.
     * Channels that are no longer in the playlist are removed.
     *
     * @param playlistId the playlist
     * @param entries    the playlist's current entries
     * @return the number of channels the playlist now has
     */
    public int replaceChannels(@NotNull UUID playlistId, @NotNull List<M3UEntry> entries) throws SQLException {
        Map<String, M3UEntry> identified = identify(entries);
        long now = System.currentTimeMillis();

        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, String> removed = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT id, identity FROM channels WHERE playlist_id = ?")) {
                    statement.setString(1, playlistId.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            removed.put(resultSet.getString("identity"), resultSet.getString("id"));
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO channels (id, playlist_id, identity, position, name, group_title, logo_url, stream_url, tvg_id, channel_number, first_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                                "ON CONFLICT (playlist_id, identity) DO UPDATE SET position = excluded.position, name = excluded.name, group_title = excluded.group_title, " +
                                "logo_url = excluded.logo_url, stream_url = excluded.stream_url, tvg_id = excluded.tvg_id, channel_number = excluded.channel_number " +
                                // Leave unchanged channels alone, most of them are on every refresh, and rewriting them would only wear the disk
                                "WHERE (channels.position, channels.name, channels.group_title, channels.logo_url, channels.stream_url, channels.tvg_id, channels.channel_number) " +
                                "IS NOT (excluded.position, excluded.name, excluded.group_title, excluded.logo_url, excluded.stream_url, excluded.tvg_id, excluded.channel_number)"
                )) {
                    int position = 0;
                    for (Map.Entry<String, M3UEntry> identifiedEntry : identified.entrySet()) {
                        M3UEntry entry = identifiedEntry.getValue();
                        statement.setString(1, UUID.randomUUID().toString());
                        statement.setString(2, playlistId.toString());
                        statement.setString(3, identifiedEntry.getKey());
                        statement.setInt(4, position++);
                        statement.setString(5, entry.name());
                        statement.setString(6, entry.groupTitle());
                        statement.setString(7, entry.logoUrl());
                        statement.setString(8, entry.url());
                        statement.setString(9, entry.tvgId());
                        if (entry.channelNumber() != null) {
                            statement.setInt(10, entry.channelNumber());
                        } else {
                            statement.setNull(10, Types.INTEGER);
                        }
                        statement.setLong(11, now);
                        statement.addBatch();

                        removed.remove(identifiedEntry.getKey());
                    }
                    statement.executeBatch();
                }

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM channels WHERE id = ?")) {
                    for (String id : removed.values()) {
                        statement.setString(1, id);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                connection.commit();
                return identified.size();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    /**
     * Determines each entry's identity within its playlist, which decides whether it's still the same channel after a
     * refresh. That's its tvg-id, unless several entries share one (e.g. HD and SD variants), then it's the stream URL.
     * Entries with the same identity as an earlier entry are dropped.
     *
     * @return the entries by identity, in playlist order
     */
    @NotNull
    static Map<String, M3UEntry> identify(@NotNull List<M3UEntry> entries) {
        Map<String, Long> tvgIdCounts = entries.stream()
                .map(M3UEntry::tvgId)
                .filter((tvgId) -> tvgId != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<String, M3UEntry> identified = new LinkedHashMap<>();
        for (M3UEntry entry : entries) {
            String identity = entry.tvgId() != null && tvgIdCounts.get(entry.tvgId()) == 1
                    ? "tvg-id:" + entry.tvgId()
                    : "url:" + entry.url();
            identified.putIfAbsent(identity, entry);
        }
        return identified;
    }

    @NotNull
    private static Channel readChannel(@NotNull ResultSet resultSet) throws SQLException {
        Integer channelNumber = resultSet.getInt("channel_number");
        if (resultSet.wasNull()) {
            channelNumber = null;
        }

        return new Channel(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("playlist_id")),
                resultSet.getString("name"),
                resultSet.getString("group_title"),
                resultSet.getString("logo_url"),
                resultSet.getString("stream_url"),
                resultSet.getString("tvg_id"),
                resultSet.getString("epg_channel_override"),
                channelNumber
        );
    }

}
