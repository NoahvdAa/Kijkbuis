package nl.noahvanderaa.kijkbuis.watchhistory.page;

import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import nl.noahvanderaa.kijkbuis.api.ui.DateRangeInput;
import nl.noahvanderaa.kijkbuis.api.ui.Filter;
import nl.noahvanderaa.kijkbuis.api.ui.FormValues;
import nl.noahvanderaa.kijkbuis.api.ui.PageProvider;
import nl.noahvanderaa.kijkbuis.api.ui.Section;
import nl.noahvanderaa.kijkbuis.api.ui.Table;
import nl.noahvanderaa.kijkbuis.api.user.User;
import nl.noahvanderaa.kijkbuis.watchhistory.WatchHistoryPlugin;
import nl.noahvanderaa.kijkbuis.watchhistory.util.TimeUtil;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class OverviewPage implements PageProvider {

    private static final ThreadLocal<DateTimeFormatter> DATE_TIME_FORMATTER = ThreadLocal.withInitial(() -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    private final WatchHistoryPlugin plugin;

    public OverviewPage(@NotNull WatchHistoryPlugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public List<Section> sections(@NotNull User viewer, @NotNull FormValues filters) throws Exception {
        Filter dateFilter = new Filter();
        DateRange selectedRange = filters.dateRange("range");
        if (selectedRange == null) {
            selectedRange = new DateRange(
                    LocalDate.now().minusDays(7),
                    LocalDate.now(),
                    filters.timeZone()
            );
        }
        dateFilter.add(new DateRangeInput("range", "Date Range").value(selectedRange));
        dateFilter.submitLabel("Apply");

        Table overviewTable = new Table("User", "Last Seen", "Last Channel", "Sessions", "Total Playtime");
        overviewTable.emptyText("Couldn't find any watch history for that time period! :(");

        try (
                Connection connection = this.plugin.context().database().getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT count(*) AS sessions, sum(ended_at - started_at) AS total_playtime, max(ended_at) AS last_seen, channel_name as last_channel, user_id FROM playback_sessions WHERE started_at < ? AND ended_at > ? GROUP BY user_id ORDER BY last_seen DESC")
        ) {
            ps.setLong(1, selectedRange.to().plusDays(1).atStartOfDay(filters.timeZone()).toEpochSecond() * 1000);
            ps.setLong(2, selectedRange.from().atStartOfDay(filters.timeZone()).toEpochSecond() * 1000);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("user_id");
                    User user = this.plugin.context().users().find(UUID.fromString(userId));
                    if (user == null) {
                        continue;
                    }

                    String lastSeen = DATE_TIME_FORMATTER.get().format(
                            Instant.ofEpochMilli(rs.getLong("last_seen")).atZone(filters.timeZone())
                    );
                    String playtime = TimeUtil.formatDuration(rs.getLong("total_playtime"));

                    overviewTable.row(
                            user.displayName(),
                            lastSeen,
                            rs.getString("last_channel"),
                            rs.getInt("sessions"),
                            playtime
                    );
                }
            }
        }

        return List.of(new Section("User Overview", dateFilter, overviewTable));
    }

}
