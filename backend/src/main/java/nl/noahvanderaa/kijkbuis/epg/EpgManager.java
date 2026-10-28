package nl.noahvanderaa.kijkbuis.epg;

import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.database.Database;
import nl.noahvanderaa.kijkbuis.epg.xmltv.XmltvChannel;
import nl.noahvanderaa.kijkbuis.epg.xmltv.XmltvEntry;
import nl.noahvanderaa.kijkbuis.epg.xmltv.XmltvProgramme;
import nl.noahvanderaa.kijkbuis.epg.xmltv.XmltvReader;
import nl.noahvanderaa.kijkbuis.net.UpstreamClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads playlists' XMLTV EPGs and answers what's on.
 * <p>
 * Programmes are only stored for EPG channels that a channel of the playlist uses, and only around the current time,
 * since EPG files often cover far more channels and days than needed. Imports go into a new generation that only
 * replaces the old data once complete, so viewers never see a half-imported EPG and the database is never locked for
 * the duration of a download. EPGs that haven't changed since the last import aren't imported again.
 */
public class EpgManager {

    private static final Logger LOGGER = LogManager.getLogger(EpgManager.class);
    private static final Duration DOWNLOAD_DEADLINE = Duration.ofMinutes(15);
    private static final long MAX_EPG_BYTES = 1024L * 1024 * 1024;
    private static final int BATCH_SIZE = 5000;
    private static final Duration KEEP_PAST = Duration.ofHours(12);
    private static final Duration KEEP_FUTURE = Duration.ofDays(14);
    /**
     * How long programmes without a stop time are assumed to last at most, when deciding whether to keep them.
     * Their actual end is the start of the next programme.
     */
    private static final Duration MAX_UNKNOWN_DURATION = Duration.ofDays(1);
    private static final Duration NEXT_LOOKAHEAD = Duration.ofHours(12);
    private static final String JOIN_CURRENT = "JOIN playlists p ON p.id = %1$s.playlist_id AND p.epg_generation = %1$s.generation ";
    /**
     * Goes up when importing stores something new (such as images), so EPGs that didn't change are imported again
     */
    private static final int IMPORT_VERSION = 2;

    private final Database database;
    private final UpstreamClient upstream;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    /**
     * The playlists whose EPG should be reloaded, and whether that has to happen even if it didn't change
     */
    private final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();

    public EpgManager(@NotNull Database database, @NotNull UpstreamClient upstream) {
        this.database = database;
        this.upstream = upstream;
    }

    /**
     * Reloads a playlist's EPG in the background. If it's already being reloaded, it's reloaded once more afterwards,
     * so changes made in the meantime are picked up.
     *
     * @param force whether to download the EPG even if the EPG server says it didn't change, such as when an admin
     *              asked for it. Otherwise it's only downloaded if it changed, or different data is needed from it.
     */
    public void refresh(@NotNull UUID playlistId, boolean force) {
        this.pending.merge(playlistId, force, Boolean::logicalOr);
        if (this.running.add(playlistId)) {
            this.executor.execute(() -> this.run(playlistId));
        }
    }

    public boolean isRefreshing(@NotNull UUID playlistId) {
        return this.running.contains(playlistId);
    }

    public void close() {
        this.executor.shutdownNow();
    }

    /**
     * Lists the channels in a playlist's EPG, which its channels can be mapped to.
     */
    @NotNull
    public List<EpgChannel> epgChannels(@NotNull UUID playlistId) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ec.id, ec.display_name, ec.icon_url FROM epg_channels ec " + JOIN_CURRENT.formatted("ec") +
                             "WHERE ec.playlist_id = ? ORDER BY COALESCE(ec.display_name, ec.id) COLLATE NOCASE"
             )) {
            statement.setString(1, playlistId.toString());
            List<EpgChannel> channels = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    channels.add(new EpgChannel(resultSet.getString("id"), resultSet.getString("display_name"), resultSet.getString("icon_url")));
                }
            }
            return channels;
        }
    }

    /**
     * Finds the programmes airing on the given channels in a time window.
     *
     * @return each channel's programmes in order, for channels that have any
     */
    @NotNull
    public Map<UUID, List<Programme>> programmes(@NotNull Collection<Channel> channels, @NotNull Instant from, @NotNull Instant to) throws SQLException {
        return this.query(channels, from, to, null);
    }

    /**
     * Finds what's on now and next on the given channels.
     *
     * @return the programmes by channel, for channels that have any
     */
    @NotNull
    public Map<UUID, NowNext> nowAndNext(@NotNull Collection<Channel> channels) throws SQLException {
        Instant now = Instant.now();
        Map<UUID, NowNext> nowNext = new HashMap<>();
        // The first two programmes are either what's on now and next, or only what's next if nothing is on now
        this.query(channels, now, now.plus(NEXT_LOOKAHEAD), 2).forEach((channelId, programmes) -> {
            Programme first = programmes.getFirst();
            if (first.startsAt().isAfter(now)) {
                nowNext.put(channelId, new NowNext(null, first));
            } else {
                nowNext.put(channelId, new NowNext(first, programmes.size() > 1 ? programmes.get(1) : null));
            }
        });
        return nowNext;
    }

    /**
     * @param perChannel how many programmes to return per EPG channel at most, or null for all of them
     */
    @NotNull
    private Map<UUID, List<Programme>> query(@NotNull Collection<Channel> channels, @NotNull Instant from, @NotNull Instant to,
                                             @Nullable Integer perChannel) throws SQLException {
        // Several channels can share an EPG channel, such as HD and SD versions
        Map<UUID, Map<String, List<UUID>>> channelIds = new HashMap<>();
        for (Channel channel : channels) {
            if (channel.epgChannelId() != null) {
                channelIds.computeIfAbsent(channel.playlistId(), (playlistId) -> new HashMap<>())
                        .computeIfAbsent(channel.epgChannelId(), (epgChannelId) -> new ArrayList<>())
                        .add(channel.id());
            }
        }
        if (channelIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> playlistIds = new ArrayList<>(channelIds.keySet());
        String sql = "SELECT pr.playlist_id, pr.epg_channel_id, pr.starts_at, pr.ends_at, pr.title, pr.subtitle, pr.description, pr.category, " +
                "pr.icon_url, pr.poster_url, pr.backdrop_url" +
                (perChannel != null ? ", ROW_NUMBER() OVER (PARTITION BY pr.playlist_id, pr.epg_channel_id ORDER BY pr.starts_at) AS number " : " ") +
                "FROM programmes pr " + JOIN_CURRENT.formatted("pr") +
                "WHERE pr.playlist_id IN (" + String.join(", ", Collections.nCopies(playlistIds.size(), "?")) + ") " +
                "AND pr.starts_at < ? AND pr.ends_at > ?";
        sql = perChannel != null ? "SELECT * FROM (" + sql + ") WHERE number <= ? ORDER BY starts_at" : sql + " ORDER BY pr.starts_at";

        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (UUID playlistId : playlistIds) {
                statement.setString(index++, playlistId.toString());
            }
            statement.setLong(index++, to.toEpochMilli());
            statement.setLong(index++, from.toEpochMilli());
            if (perChannel != null) {
                statement.setInt(index, perChannel);
            }

            Map<UUID, List<Programme>> programmes = new HashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    List<UUID> ids = channelIds.get(UUID.fromString(resultSet.getString("playlist_id"))).get(resultSet.getString("epg_channel_id"));
                    if (ids == null) {
                        continue;
                    }

                    Programme programme = new Programme(
                            Instant.ofEpochMilli(resultSet.getLong("starts_at")),
                            Instant.ofEpochMilli(resultSet.getLong("ends_at")),
                            resultSet.getString("title"),
                            resultSet.getString("subtitle"),
                            resultSet.getString("description"),
                            resultSet.getString("category"),
                            resultSet.getString("icon_url"),
                            resultSet.getString("poster_url"),
                            resultSet.getString("backdrop_url")
                    );
                    for (UUID id : ids) {
                        programmes.computeIfAbsent(id, (channelId) -> new ArrayList<>()).add(programme);
                    }
                }
            }
            return programmes;
        }
    }

    private void run(@NotNull UUID playlistId) {
        try {
            Boolean force;
            while ((force = this.pending.remove(playlistId)) != null) {
                try {
                    this.doRefresh(playlistId, force);
                } catch (Exception ex) {
                    LOGGER.error("Failed to refresh the EPG of playlist {}", playlistId, ex);
                }
            }
        } finally {
            this.running.remove(playlistId);
            // Another refresh may have been requested after the loop ended, but before it was marked as not running
            if (this.pending.containsKey(playlistId) && this.running.add(playlistId)) {
                this.executor.execute(() -> this.run(playlistId));
            }
        }
    }

    private void doRefresh(@NotNull UUID playlistId, boolean force) throws SQLException {
        Source source = this.findSource(playlistId);
        if (source == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (source.url() == null) {
            // No EPG configured (anymore), so there is no EPG data
            this.activate(playlistId, null, now, null);
            return;
        }

        // Only ask for the EPG if it changed when the current data came from the same URL, for the same channels
        Set<String> usedEpgChannels = this.usedEpgChannels(playlistId);
        String cacheKey = cacheKey(source.url(), usedEpgChannels);
        Map<String, String> headers = new HashMap<>();
        if (!force && source.generation() != null && cacheKey.equals(source.cacheKey())) {
            if (source.etag() != null) {
                headers.put("If-None-Match", source.etag());
            }
            if (source.lastModified() != null) {
                headers.put("If-Modified-Since", source.lastModified());
            }
        }

        long generation = source.generation() != null ? source.generation() + 1 : 1;
        try {
            this.deleteGenerations(playlistId, source.generation());
            try (UpstreamClient.Download download = this.upstream.download(source.url(), headers, DOWNLOAD_DEADLINE, MAX_EPG_BYTES)) {
                if (download.status() == 304) {
                    this.keepCurrent(playlistId, now);
                    LOGGER.info("The EPG of {} didn't change", source.name());
                    return;
                }
                if (download.status() != 200) {
                    throw new IOException("The EPG URL responded with HTTP " + download.status());
                }

                int programmes = this.importEpg(playlistId, generation, download.body(), usedEpgChannels);
                this.activate(playlistId, generation, now, new CacheInfo(
                        download.headers().firstValue("ETag").orElse(null),
                        download.headers().firstValue("Last-Modified").orElse(null),
                        cacheKey
                ));
                LOGGER.info("Refreshed the EPG of {}, it has {} programmes for its channels", source.name(), programmes);
            }
        } catch (IOException ex) {
            this.deleteGenerations(playlistId, source.generation());
            String error = UpstreamClient.describe(ex);
            LOGGER.warn("Failed to refresh the EPG of {}: {}", source.name(), error);
            try (Connection connection = this.database.connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE playlists SET epg_last_refresh_attempt_at = ?, epg_last_refresh_error = ? WHERE id = ?"
                 )) {
                statement.setLong(1, now);
                statement.setString(2, error);
                statement.setString(3, playlistId.toString());
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            if (this.findSource(playlistId) == null) {
                // The playlist was removed during the import
                return;
            }
            this.deleteGenerations(playlistId, source.generation());
            throw ex;
        }
    }

    /**
     * Imports an EPG into the given generation.
     *
     * @param usedEpgChannels the EPG channels to store programmes for
     * @return the number of programmes stored
     */
    private int importEpg(@NotNull UUID playlistId, long generation, @NotNull InputStream body, @NotNull Set<String> usedEpgChannels) throws IOException, SQLException {
        Instant now = Instant.now();
        Instant keepAfter = now.minus(KEEP_PAST);
        Instant keepBefore = now.plus(KEEP_FUTURE);

        try (XmltvReader reader = XmltvReader.open(body);
             Connection connection = this.database.connection();
             PreparedStatement insertChannel = connection.prepareStatement(
                     "INSERT OR IGNORE INTO epg_channels (playlist_id, generation, id, display_name, icon_url) VALUES (?, ?, ?, ?, ?)"
             );
             PreparedStatement insertProgramme = connection.prepareStatement(
                     "INSERT OR IGNORE INTO programmes (playlist_id, generation, epg_channel_id, starts_at, ends_at, title, subtitle, description, category, " +
                             "icon_url, poster_url, backdrop_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )) {
            // Commit in batches rather than at the end, so other writes don't have to wait for the whole download
            connection.setAutoCommit(false);
            int entries = 0;
            int programmes = 0;
            int batched = 0;

            XmltvEntry entry;
            while ((entry = reader.next()) != null) {
                entries++;
                switch (entry) {
                    case XmltvChannel channel -> {
                        insertChannel.setString(1, playlistId.toString());
                        insertChannel.setLong(2, generation);
                        insertChannel.setString(3, channel.id());
                        insertChannel.setString(4, channel.displayName());
                        insertChannel.setString(5, channel.iconUrl());
                        insertChannel.addBatch();
                        batched++;
                    }
                    case XmltvProgramme programme -> {
                        Instant end = programme.stop() != null ? programme.stop() : programme.start().plus(MAX_UNKNOWN_DURATION);
                        if (!usedEpgChannels.contains(programme.channelId()) || !programme.start().isBefore(keepBefore) || !end.isAfter(keepAfter)) {
                            continue;
                        }

                        insertProgramme.setString(1, playlistId.toString());
                        insertProgramme.setLong(2, generation);
                        insertProgramme.setString(3, programme.channelId());
                        insertProgramme.setLong(4, programme.start().toEpochMilli());
                        if (programme.stop() != null) {
                            insertProgramme.setLong(5, programme.stop().toEpochMilli());
                        } else {
                            insertProgramme.setNull(5, Types.INTEGER);
                        }
                        insertProgramme.setString(6, programme.title());
                        insertProgramme.setString(7, programme.subtitle());
                        insertProgramme.setString(8, programme.description());
                        insertProgramme.setString(9, programme.category());
                        insertProgramme.setString(10, programme.iconUrl());
                        insertProgramme.setString(11, programme.posterUrl());
                        insertProgramme.setString(12, programme.backdropUrl());
                        insertProgramme.addBatch();
                        programmes++;
                        batched++;
                    }
                }

                if (batched >= BATCH_SIZE) {
                    insertChannel.executeBatch();
                    insertProgramme.executeBatch();
                    connection.commit();
                    batched = 0;
                }
            }
            insertChannel.executeBatch();
            insertProgramme.executeBatch();
            connection.commit();

            if (entries == 0) {
                throw new IOException("The EPG contains no channels or programmes");
            }

            // Programmes without a stop time end when the next one starts. The last one has no known end, so drop it.
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE programmes SET ends_at = (SELECT MIN(next.starts_at) FROM programmes next WHERE next.playlist_id = programmes.playlist_id " +
                            "AND next.generation = programmes.generation AND next.epg_channel_id = programmes.epg_channel_id AND next.starts_at > programmes.starts_at) " +
                            "WHERE playlist_id = ? AND generation = ? AND ends_at IS NULL"
            )) {
                statement.setString(1, playlistId.toString());
                statement.setLong(2, generation);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM programmes WHERE playlist_id = ? AND generation = ? AND ends_at IS NULL"
            )) {
                statement.setString(1, playlistId.toString());
                statement.setLong(2, generation);
                programmes -= statement.executeUpdate();
            }
            connection.commit();

            return programmes;
        }
    }

    /**
     * Makes a generation the playlist's current EPG data, and removes all other generations.
     *
     * @param generation the generation, or null to remove all EPG data
     * @param cache      what the EPG server said about the version that was imported, if anything was imported
     */
    private void activate(@NotNull UUID playlistId, @Nullable Long generation, long now, @Nullable CacheInfo cache) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE playlists SET epg_generation = ?, epg_etag = ?, epg_last_modified = ?, epg_cache_key = ?, " +
                                "epg_last_refresh_attempt_at = ?, epg_last_refreshed_at = ?, epg_last_refresh_error = NULL WHERE id = ?"
                )) {
                    statement.setObject(1, generation);
                    statement.setString(2, cache != null ? cache.etag() : null);
                    statement.setString(3, cache != null ? cache.lastModified() : null);
                    statement.setString(4, cache != null ? cache.cacheKey() : null);
                    statement.setLong(5, now);
                    statement.setObject(6, generation != null ? now : null);
                    statement.setString(7, playlistId.toString());
                    statement.executeUpdate();
                }
                deleteGenerations(connection, playlistId, generation);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    /**
     * Keeps the current EPG data because the EPG didn't change, only removing programmes that are long over.
     */
    private void keepCurrent(@NotNull UUID playlistId, long now) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE playlists SET epg_last_refresh_attempt_at = ?, epg_last_refreshed_at = ?, epg_last_refresh_error = NULL WHERE id = ?"
                )) {
                    statement.setLong(1, now);
                    statement.setLong(2, now);
                    statement.setString(3, playlistId.toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM programmes WHERE playlist_id = ? AND ends_at <= ?")) {
                    statement.setString(1, playlistId.toString());
                    statement.setLong(2, now - KEEP_PAST.toMillis());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    /**
     * Removes all EPG data of a playlist except the given generation.
     */
    private void deleteGenerations(@NotNull UUID playlistId, @Nullable Long keep) throws SQLException {
        try (Connection connection = this.database.connection()) {
            deleteGenerations(connection, playlistId, keep);
        }
    }

    private static void deleteGenerations(@NotNull Connection connection, @NotNull UUID playlistId, @Nullable Long keep) throws SQLException {
        for (String table : List.of("programmes", "epg_channels")) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE playlist_id = ? AND generation IS NOT ?")) {
                statement.setString(1, playlistId.toString());
                statement.setObject(2, keep);
                statement.executeUpdate();
            }
        }
    }

    /**
     * @return the EPG channel IDs that the playlist's channels use
     */
    @NotNull
    private Set<String> usedEpgChannels(@NotNull UUID playlistId) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT DISTINCT COALESCE(epg_channel_override, tvg_id) AS epg_channel_id FROM channels " +
                             "WHERE playlist_id = ? AND COALESCE(epg_channel_override, tvg_id) IS NOT NULL"
             )) {
            statement.setString(1, playlistId.toString());
            Set<String> ids = new HashSet<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getString("epg_channel_id"));
                }
            }
            return ids;
        }
    }

    @Nullable
    private Source findSource(@NotNull UUID playlistId) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name, epg_url, detected_epg_url, epg_generation, epg_etag, epg_last_modified, epg_cache_key FROM playlists WHERE id = ?"
             )) {
            statement.setString(1, playlistId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                String url = resultSet.getString("epg_url") != null ? resultSet.getString("epg_url") : resultSet.getString("detected_epg_url");
                long generation = resultSet.getLong("epg_generation");
                return new Source(
                        resultSet.getString("name"),
                        url,
                        resultSet.wasNull() ? null : generation,
                        resultSet.getString("epg_etag"),
                        resultSet.getString("epg_last_modified"),
                        resultSet.getString("epg_cache_key")
                );
            }
        }
    }

    /**
     * Identifies what the imported EPG data depends on: how it was imported, the EPG URL, and which of its channels
     * were imported.
     */
    @NotNull
    private static String cacheKey(@NotNull String url, @NotNull Set<String> usedEpgChannels) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((IMPORT_VERSION + "\n" + url).getBytes(StandardCharsets.UTF_8));
            for (String epgChannelId : new TreeSet<>(usedEpgChannels)) {
                digest.update((byte) '\n');
                digest.update(epgChannelId.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * @param now  what's on now, if anything
     * @param next what's on after that, if known
     */
    public record NowNext(@Nullable Programme now, @Nullable Programme next) {
    }

    /**
     * @param name         the playlist name
     * @param url          the EPG URL to use, if any
     * @param generation   the current generation of EPG data, if any
     * @param etag         the ETag of the EPG version that was last imported
     * @param lastModified the Last-Modified date of the EPG version that was last imported
     * @param cacheKey     the cache key of the data that was last imported, see {@link #cacheKey}
     */
    private record Source(@NotNull String name, @Nullable String url, @Nullable Long generation,
                          @Nullable String etag, @Nullable String lastModified, @Nullable String cacheKey) {
    }

    private record CacheInfo(@Nullable String etag, @Nullable String lastModified, @NotNull String cacheKey) {
    }

}
