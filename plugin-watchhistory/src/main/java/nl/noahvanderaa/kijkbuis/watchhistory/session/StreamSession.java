package nl.noahvanderaa.kijkbuis.watchhistory.session;

import nl.noahvanderaa.kijkbuis.api.epg.Programme;
import nl.noahvanderaa.kijkbuis.api.scheduler.Task;
import nl.noahvanderaa.kijkbuis.api.stream.Stream;
import nl.noahvanderaa.kijkbuis.watchhistory.WatchHistoryPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StreamSession {

    private final WatchHistoryPlugin plugin;
    private final Stream stream;
    private final Instant startedAt = Instant.now();
    private final Set<Programme> programmes = ConcurrentHashMap.newKeySet();
    private Task nextCheckInTask;

    public StreamSession(@NotNull WatchHistoryPlugin plugin, @NotNull Stream stream) {
        this.plugin = plugin;
        this.stream = stream;
        this.onProgrammeSwitch();
    }

    public void cleanup() {
        this.plugin.context().scheduler().run(this::save);
        if (this.nextCheckInTask != null) {
            this.nextCheckInTask.cancel();
        }
    }

    public void save() {
        long now = System.currentTimeMillis();

        try (
                Connection connection = this.plugin.context().database().getConnection();
                PreparedStatement sessionStatement = connection.prepareStatement(
                        "INSERT INTO playback_sessions (id, user_id, channel_id, channel_name, started_at, ended_at, flags) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET ended_at = EXCLUDED.ended_at"
                );
                PreparedStatement programmeStatement = connection.prepareStatement(
                        "INSERT INTO playback_session_programmes (playback_session_id, started_at, ended_at, name, description) VALUES (?, ?, ?, ?, ?) ON CONFLICT (playback_session_id, started_at) DO UPDATE SET ended_at = EXCLUDED.ended_at"
                )
        ) {
            connection.setAutoCommit(false);

            sessionStatement.setString(1, this.stream.id().toString());
            sessionStatement.setString(2, this.stream.owner().id().toString());
            sessionStatement.setString(3, this.stream.channel().id().toString());
            sessionStatement.setString(4, this.stream.channel().name());
            sessionStatement.setLong(5, this.startedAt.toEpochMilli());
            sessionStatement.setLong(6, now);
            sessionStatement.setInt(7, this.stream.transcoding() ? 1 : 0); // once we have more flags we'll do proper bitwise OR
            sessionStatement.executeUpdate();

            for (Programme programme : this.programmes) {
                programmeStatement.setString(1, this.stream.id().toString());
                // Clamp the timestamps to the actual session duration.
                programmeStatement.setLong(2, Math.max(programme.startsAt().toEpochMilli(), this.startedAt.toEpochMilli()));
                programmeStatement.setLong(3, Math.min(programme.endsAt().toEpochMilli(), now));
                programmeStatement.setString(4, programme.title());
                programmeStatement.setString(5, programme.description());
                programmeStatement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            this.plugin.context().logger().log(System.Logger.Level.ERROR, "Failed to save stream session for stream " + this.stream.id(), e);
        }
    }

    private void onProgrammeSwitch() {
        Programme programme = this.plugin.context().guide().now(this.stream.channel());
        Duration nextCheckIn = Duration.ofMinutes(1);
        if (programme != null) {
            nextCheckIn = Duration.between(Instant.now(), programme.endsAt());
            // Set can only contain unique elements, so we don't need to check for dupes.
            this.programmes.add(programme);
        } else {
            Programme next = this.plugin.context().guide().next(this.stream.channel());
            if (next != null) {
                nextCheckIn = Duration.between(Instant.now(), next.startsAt());
            }
        }

        if (this.stream.isRunning()) {
            if (nextCheckIn.isNegative()) {
                nextCheckIn = Duration.ZERO;
            }
            this.nextCheckInTask = this.plugin.context().scheduler().runLater(nextCheckIn, this::onProgrammeSwitch);
        }
    }

}
