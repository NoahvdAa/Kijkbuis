package nl.noahvanderaa.kijkbuis.playlist;

import nl.noahvanderaa.kijkbuis.channel.ChannelManager;
import nl.noahvanderaa.kijkbuis.database.Database;
import nl.noahvanderaa.kijkbuis.epg.EpgManager;
import nl.noahvanderaa.kijkbuis.net.UpstreamClient;
import nl.noahvanderaa.kijkbuis.playlist.m3u.M3UParser;
import nl.noahvanderaa.kijkbuis.playlist.m3u.M3UPlaylist;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class PlaylistManager {

    private static final Logger LOGGER = LogManager.getLogger(PlaylistManager.class);
    private static final Duration DOWNLOAD_DEADLINE = Duration.ofMinutes(5);
    private static final long MAX_PLAYLIST_BYTES = 256L * 1024 * 1024;
    private static final String SELECT_PLAYLISTS = "SELECT p.*, (SELECT COUNT(*) FROM channels c WHERE c.playlist_id = p.id) AS channel_count FROM playlists p ";

    private final Database database;
    private final ChannelManager channelManager;
    private final EpgManager epgManager;
    private final UpstreamClient upstream;
    private final ExecutorService refreshExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, CompletableFuture<Playlist>> refreshes = new ConcurrentHashMap<>();

    public PlaylistManager(@NotNull Database database, @NotNull ChannelManager channelManager, @NotNull EpgManager epgManager, @NotNull UpstreamClient upstream) {
        this.database = database;
        this.channelManager = channelManager;
        this.epgManager = epgManager;
        this.upstream = upstream;
    }

    @NotNull
    public List<Playlist> list() throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_PLAYLISTS + "ORDER BY p.created_at, p.id");
             ResultSet resultSet = statement.executeQuery()) {
            List<Playlist> playlists = new ArrayList<>();
            while (resultSet.next()) {
                playlists.add(this.readPlaylist(resultSet));
            }
            return playlists;
        }
    }

    @NotNull
    public List<Playlist> visibleTo(@NotNull User user) throws SQLException {
        // Every user can see every playlist for now, this is where group-based visibility goes
        return this.list();
    }

    @Nullable
    public Playlist find(@NotNull UUID id) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_PLAYLISTS + "WHERE p.id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? this.readPlaylist(resultSet) : null;
            }
        }
    }

    /**
     * @return the playlist, or null if it doesn't exist or the user may not see it
     */
    @Nullable
    public Playlist findVisibleTo(@NotNull User user, @NotNull UUID id) throws SQLException {
        // Every user can see every playlist for now, this is where group-based visibility goes
        return this.find(id);
    }

    /**
     * Adds a playlist and loads its channels right away.
     */
    @NotNull
    public Playlist create(@NotNull String name, @NotNull String url, @Nullable String epgUrl, @NotNull Duration refreshInterval) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO playlists (id, name, url, epg_url, refresh_interval_minutes, created_at) VALUES (?, ?, ?, ?, ?, ?)"
             )) {
            statement.setString(1, id.toString());
            statement.setString(2, name);
            statement.setString(3, url);
            statement.setString(4, epgUrl);
            statement.setLong(5, refreshInterval.toMinutes());
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
        LOGGER.info("Added playlist {}", name);

        Playlist playlist = this.refreshNow(id);
        if (playlist == null) {
            throw new IllegalStateException("Playlist " + name + " was removed while it was being added");
        }
        return playlist;
    }

    /**
     * Changes a playlist. If its URL changed, its channels are reloaded right away. If its EPG URL changed, the EPG
     * is reloaded in the background.
     *
     * @return the updated playlist, or null if it doesn't exist
     */
    @Nullable
    public Playlist update(@NotNull UUID id, @NotNull PlaylistChanges changes) throws SQLException {
        Playlist playlist = this.find(id);
        if (playlist == null) {
            return null;
        }

        String url = changes.url() != null ? changes.url() : playlist.url();
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE playlists SET name = ?, url = ?, epg_url = ?, refresh_interval_minutes = ? WHERE id = ?"
             )) {
            statement.setString(1, changes.name() != null ? changes.name() : playlist.name());
            statement.setString(2, url);
            statement.setString(3, changes.epgUrlChanged() ? changes.epgUrl() : playlist.epgUrl());
            statement.setLong(4, (changes.refreshInterval() != null ? changes.refreshInterval() : playlist.refreshInterval()).toMinutes());
            statement.setString(5, id.toString());
            if (statement.executeUpdate() == 0) {
                return null;
            }
        }

        if (!url.equals(playlist.url())) {
            // Refreshing the playlist refreshes the EPG as well
            return this.refreshNow(id);
        }
        if (changes.epgUrlChanged() && !Objects.equals(changes.epgUrl(), playlist.epgUrl())) {
            this.epgManager.refresh(id, false);
        }
        return this.find(id);
    }

    /**
     * Removes a playlist and its channels.
     *
     * @return whether the playlist existed
     */
    public boolean delete(@NotNull UUID id) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM playlists WHERE id = ?")) {
            statement.setString(1, id.toString());
            boolean deleted = statement.executeUpdate() > 0;
            if (deleted) {
                LOGGER.info("Removed playlist {}", id);
            }
            return deleted;
        }
    }

    /**
     * Refreshes a playlist and waits for it to finish. If the playlist is already being refreshed, this waits for
     * that refresh instead. Failing to load the playlist isn't an error, it's recorded in the playlist's
     * lastRefreshError. Its EPG is downloaded again afterwards, in the background, even if the EPG server says it
     * didn't change: whoever asked for this wants fresh data.
     *
     * @return the refreshed playlist, or null if it doesn't exist
     */
    @Nullable
    public Playlist refreshNow(@NotNull UUID id) throws SQLException {
        try {
            return this.refresh(id, true).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof SQLException sqlException) {
                throw sqlException;
            }
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    /**
     * Starts refreshing every playlist that is due for a refresh, without waiting for them to finish.
     */
    public void refreshDue() throws SQLException {
        Instant now = Instant.now();
        for (Playlist playlist : this.list()) {
            if (!playlist.nextRefreshAt().isAfter(now)) {
                this.refresh(playlist.id(), false);
            }
        }
    }

    public void close() {
        this.refreshExecutor.shutdownNow();
    }

    /**
     * Refreshes a playlist in the background. Only one refresh per playlist runs at a time.
     *
     * @param forceEpg whether to download the EPG afterwards even if it didn't change, see {@link EpgManager#refresh}
     */
    @NotNull
    private CompletableFuture<Playlist> refresh(@NotNull UUID id, boolean forceEpg) {
        CompletableFuture<Playlist> future = new CompletableFuture<>();
        CompletableFuture<Playlist> running = this.refreshes.putIfAbsent(id, future);
        if (running != null) {
            if (forceEpg) {
                // The running refresh may not download the EPG, so make sure it's downloaded once that's done
                return running.thenApply((playlist) -> {
                    this.epgManager.refresh(id, true);
                    return playlist;
                });
            }
            return running;
        }

        try {
            this.refreshExecutor.execute(() -> {
                try {
                    Playlist playlist = this.doRefresh(id, forceEpg);
                    this.refreshes.remove(id, future);
                    future.complete(playlist);
                } catch (Throwable ex) {
                    LOGGER.error("Failed to refresh playlist {}", id, ex);
                    this.refreshes.remove(id, future);
                    future.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            this.refreshes.remove(id, future);
            future.completeExceptionally(ex);
        }
        return future;
    }

    @Nullable
    private Playlist doRefresh(@NotNull UUID id, boolean forceEpg) throws SQLException {
        Playlist playlist = this.find(id);
        if (playlist == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        try {
            M3UPlaylist parsed = this.fetch(playlist.url());
            if (parsed.entries().isEmpty()) {
                // Rather keep the old channels than wipe them because a provider briefly served an empty playlist
                throw new IOException("The playlist contains no channels");
            }

            int channels = this.channelManager.replaceChannels(id, parsed.entries());
            try (Connection connection = this.database.connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE playlists SET last_refresh_attempt_at = ?, last_refreshed_at = ?, last_refresh_error = NULL, detected_epg_url = ? WHERE id = ?"
                 )) {
                statement.setLong(1, now);
                statement.setLong(2, now);
                statement.setString(3, parsed.epgUrl());
                statement.setString(4, id.toString());
                statement.executeUpdate();
            }
            LOGGER.info("Refreshed playlist {}, it has {} channels", playlist.name(), channels);
        } catch (IOException ex) {
            String error = UpstreamClient.describe(ex);
            LOGGER.warn("Failed to refresh playlist {}: {}", playlist.name(), error);
            try (Connection connection = this.database.connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE playlists SET last_refresh_attempt_at = ?, last_refresh_error = ? WHERE id = ?"
                 )) {
                statement.setLong(1, now);
                statement.setString(2, error);
                statement.setString(3, id.toString());
                statement.executeUpdate();
            }
        }

        // Also when the playlist failed to load, the EPG is often hosted elsewhere and may be fine
        this.epgManager.refresh(id, forceEpg);
        return this.find(id);
    }

    @NotNull
    private M3UPlaylist fetch(@NotNull String url) throws IOException {
        try (UpstreamClient.Download download = this.upstream.download(url, Map.of(), DOWNLOAD_DEADLINE, MAX_PLAYLIST_BYTES)) {
            if (download.status() != 200) {
                throw new IOException("The playlist URL responded with HTTP " + download.status());
            }
            return M3UParser.parse(new BufferedReader(new InputStreamReader(download.body(), StandardCharsets.UTF_8)));
        }
    }

    @NotNull
    private Playlist readPlaylist(@NotNull ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        return new Playlist(
                id,
                resultSet.getString("name"),
                resultSet.getString("url"),
                resultSet.getString("epg_url"),
                resultSet.getString("detected_epg_url"),
                Duration.ofMinutes(resultSet.getLong("refresh_interval_minutes")),
                readInstant(resultSet, "last_refresh_attempt_at"),
                readInstant(resultSet, "last_refreshed_at"),
                resultSet.getString("last_refresh_error"),
                readInstant(resultSet, "epg_last_refresh_attempt_at"),
                readInstant(resultSet, "epg_last_refreshed_at"),
                resultSet.getString("epg_last_refresh_error"),
                this.epgManager.isRefreshing(id),
                resultSet.getInt("channel_count"),
                Instant.ofEpochMilli(resultSet.getLong("created_at"))
        );
    }

    @Nullable
    private static Instant readInstant(@NotNull ResultSet resultSet, @NotNull String column) throws SQLException {
        long millis = resultSet.getLong(column);
        return resultSet.wasNull() ? null : Instant.ofEpochMilli(millis);
    }

}
