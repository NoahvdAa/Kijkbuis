package nl.noahvanderaa.kijkbuis.testplugin;

import nl.noahvanderaa.kijkbuis.api.epg.Programme;
import nl.noahvanderaa.kijkbuis.api.event.EventHandler;
import nl.noahvanderaa.kijkbuis.api.event.EventPriority;
import nl.noahvanderaa.kijkbuis.api.event.Listener;
import nl.noahvanderaa.kijkbuis.api.event.stream.StreamStartedEvent;
import nl.noahvanderaa.kijkbuis.api.event.stream.StreamStartingEvent;
import nl.noahvanderaa.kijkbuis.api.event.stream.StreamStoppedEvent;
import nl.noahvanderaa.kijkbuis.api.plugin.KijkbuisPlugin;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginContext;
import nl.noahvanderaa.kijkbuis.api.stream.Stream;
import nl.noahvanderaa.kijkbuis.api.ui.Checkbox;
import nl.noahvanderaa.kijkbuis.api.ui.DateRange;
import nl.noahvanderaa.kijkbuis.api.ui.DateRangeInput;
import nl.noahvanderaa.kijkbuis.api.ui.Filter;
import nl.noahvanderaa.kijkbuis.api.ui.Form;
import nl.noahvanderaa.kijkbuis.api.ui.FormException;
import nl.noahvanderaa.kijkbuis.api.ui.ItemList;
import nl.noahvanderaa.kijkbuis.api.ui.ListItem;
import nl.noahvanderaa.kijkbuis.api.ui.NumberInput;
import nl.noahvanderaa.kijkbuis.api.ui.Section;
import nl.noahvanderaa.kijkbuis.api.ui.Select;
import nl.noahvanderaa.kijkbuis.api.ui.Slot;
import nl.noahvanderaa.kijkbuis.api.ui.Stats;
import nl.noahvanderaa.kijkbuis.api.ui.Table;
import nl.noahvanderaa.kijkbuis.api.ui.Text;
import nl.noahvanderaa.kijkbuis.api.ui.TextInput;
import nl.noahvanderaa.kijkbuis.api.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A plugin for PluginTest, which blocks the channel named Blocked and writes down what it sees. Kijkbuis loads it in
 * a class loader of its own, so the test can't see this class. It reads what the plugin saw through {@link Supplier},
 * which they share since it's part of the JDK, and from the plugin's database.
 */
public class TestPlugin implements KijkbuisPlugin, Listener, Supplier<List<String>> {

    private static final Settings DEFAULT_SETTINGS = new Settings("Hello", 90, true, "missing");

    private final List<String> seen = new CopyOnWriteArrayList<>();
    private final Map<UUID, Settings> settings = new ConcurrentHashMap<>();
    private PluginContext context;

    @Override
    public void onEnable(@NotNull PluginContext context) {
        this.context = context;
        context.logger().log(System.Logger.Level.INFO, "Enabling {0} {1}", context.info().name(), context.info().version());
        this.seen.add("enabled " + context.info().id());
        this.seen.add("sees Kijkbuis: " + this.canLoad("nl.noahvanderaa.kijkbuis.KijkbuisApp"));
        this.seen.add("sees Gson: " + this.canLoad("com.google.gson.Gson"));
        context.events().register(this);

        AtomicInteger ticks = new AtomicInteger();
        context.scheduler().runRepeating(Duration.ZERO, Duration.ofMillis(10), (task) -> {
            if (ticks.incrementAndGet() == 3) {
                task.cancel();
                this.seen.add("ticked 3 times");
            }
        });
        // Still waiting when Kijkbuis stops, which doesn't hold that up
        context.scheduler().runLater(Duration.ofHours(1), () -> this.seen.add("waited an hour"));

        context.ui().register(Slot.ACCOUNT, (viewer) -> List.of(new Section("Test plugin",
                new Text("Hello, " + viewer.username()),
                new Stats().add("Streams stopped", "42").add("Watched", "12 h"),
                new ItemList()
                        .emptyText("Nothing here")
                        .add(new ListItem("Live show").subtitle("Live").detail("5 min").time(Instant.parse("2026-10-28T20:00:00Z"))
                                .imageUrl("https://example.com/live.png"))
                        .add(new ListItem("Sneaky").imageUrl("javascript:alert(1)")),
                this.settingsForm(viewer),
                // Only works on pages, so it's left out here
                new Filter().add(new TextInput("ignored", "Ignored"))
        )));
        context.ui().register(Slot.ACCOUNT, (viewer) -> {
            throw new IllegalStateException("Broken on purpose, the other sections still show");
        });
        context.ui().register(Slot.ADMIN_OVERVIEW, (viewer) -> List.of(new Section("Test plugin for admins", new ItemList())));

        context.ui().registerPage("lookup", "Lookup", (viewer, filters) -> {
            long days = Objects.requireNonNullElse(filters.number("days"), 7L);
            String who = Objects.requireNonNullElse(filters.string("who"), "");
            boolean onlyStops = filters.checked("onlyStops");
            DateRange period = filters.dateRange("period");
            ItemList seen = new ItemList().emptyText("Nothing seen yet");
            this.seen.stream().filter((line) -> !onlyStops || line.startsWith("stop ")).forEach((line) -> seen.add(new ListItem(line)));
            return List.of(new Section("What the test plugin saw",
                    new Filter()
                            .add(new NumberInput("days", "Look back").value(days).min(1).max(365).unit("days"))
                            .add(new TextInput("who", "User").value(who).placeholder("Anyone"))
                            .add(new Checkbox("onlyStops", "Only stopped streams").value(onlyStops))
                            .add(new DateRangeInput("period", "Between").value(period)),
                    new Text("Looking back " + days + " days" + (who.isEmpty() ? "" : " for " + who)),
                    seen,
                    new Table("Who", "Days", "When", "Note").row(who.isEmpty() ? "Anyone" : who, days, Instant.parse("2026-10-30T12:00:00Z"), null),
                    new Text(period != null ? "Between " + period.startsAt() + " and " + period.endsAt() + " in " + period.zone() : "Any day in " + filters.timeZone())
            ));
        }).subPage("details", "Details", (viewer, filters) -> List.of(new Section("Details", new Text("Only on a sub-page"))));
        context.ui().registerPage("broken", "Broken", (viewer, filters) -> {
            throw new IllegalStateException("Broken on purpose");
        });

        context.ui().handleForm("settings", (viewer, values) -> {
            Long days = values.number("days");
            if (days == null || days < 1 || days > 365) {
                throw new FormException("Keep history for must be between 1 and 365 days");
            }
            String quality = values.string("quality");
            if (!Set.of("auto", "low").contains(quality)) {
                throw new FormException("Pick one of the qualities");
            }
            this.settings.put(viewer.id(), new Settings(Objects.requireNonNullElse(values.string("note"), ""), days, values.checked("enabled"), quality));
        });
        context.ui().handleForm("history/clear", (viewer, values) -> this.seen.add("cleared by " + viewer.username()));
        context.ui().handleForm("broken", (viewer, values) -> {
            throw new IllegalStateException("Broken on purpose");
        });
    }

    @Override
    public void onDisable() {
        this.seen.add("disabled");
    }

    @EventHandler
    public void onStreamStart(@NotNull StreamStartingEvent event) {
        if (event.channel().name().equals("Blocked")) {
            event.cancel("The test plugin blocks this channel");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterStreamStart(@NotNull StreamStartingEvent event) {
        this.seen.add("start " + event.user().username() + " " + event.channel().name() + " " + event.quality() + (event.isCancelled() ? " cancelled" : ""));
        Instant now = Instant.now();
        this.seen.add("on " + event.channel().name() + ": " + this.titles(this.context.guide().programmes(event.channel(), now, now)));
        this.seen.add("now on " + event.channel().name() + ": " + this.title(this.context.guide().now(event.channel()))
                + ", next: " + this.title(this.context.guide().next(event.channel())));

        User found = this.context.users().find(event.user().id());
        this.seen.add("found " + (found != null ? found.username() + " (" + found.displayName() + ") with " + found.privileges() : "nobody")
                + ", and " + this.context.users().find(UUID.randomUUID()) + " for an unknown ID");
        this.seen.add("everyone: " + this.context.users().all().stream().map(User::username).collect(Collectors.joining(", ")));
    }

    @EventHandler
    public void onStreamStarted(@NotNull StreamStartedEvent event) {
        this.seen.add("started " + event.stream().id() + " " + event.stream().channel().name() + (event.stream().isRunning() ? "" : ", but not running"));
    }

    @EventHandler
    public void onStreamStopped(@NotNull StreamStoppedEvent event) throws SQLException {
        Stream stream = event.stream();
        this.seen.add("stop " + stream.id() + " " + stream.channel().name() + " " + event.reason() + (stream.isRunning() ? ", but still running" : ""));

        String programmes = this.titles(this.context.guide().programmes(stream.channel(), stream.startedAt(), Instant.now()));
        try (Connection connection = this.context.database().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO stops (stream_id, reason, programmes) VALUES (?, ?, ?)")) {
            statement.setString(1, stream.id().toString());
            statement.setString(2, event.reason().name());
            statement.setString(3, programmes);
            statement.executeUpdate();
        }
    }

    @Override
    public List<String> get() {
        return List.copyOf(this.seen);
    }

    /**
     * @return a form with the viewer's own settings in it
     */
    @NotNull
    private Form settingsForm(@NotNull User viewer) {
        Settings settings = this.settings.getOrDefault(viewer.id(), DEFAULT_SETTINGS);
        return new Form("settings")
                .add(new TextInput("note", "Note").value(settings.note()).placeholder("Anything").maxLength(100).help("Shown nowhere"))
                .add(new NumberInput("days", "Keep history for").value(settings.days()).min(1).max(365).unit("days").required(true))
                .add(new Checkbox("enabled", "Keep a history").value(settings.enabled()))
                .add(new Select("quality", "Quality").option("auto", "Auto").option("low", "Low").value(settings.quality()))
                .submitLabel("Save settings");
    }

    @NotNull
    private String title(@Nullable Programme programme) {
        return programme != null ? programme.title() : "nothing";
    }

    @NotNull
    private String titles(@NotNull List<Programme> programmes) {
        return programmes.stream().map(Programme::title).collect(Collectors.joining(", "));
    }

    private boolean canLoad(@NotNull String className) {
        try {
            Class.forName(className, false, this.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private record Settings(@NotNull String note, long days, boolean enabled, @NotNull String quality) {
    }

}
