package nl.noahvanderaa.kijkbuis.plugin;

import com.zaxxer.hikari.HikariDataSource;
import nl.noahvanderaa.kijkbuis.api.epg.Guide;
import nl.noahvanderaa.kijkbuis.api.event.EventManager;
import nl.noahvanderaa.kijkbuis.api.event.Listener;
import nl.noahvanderaa.kijkbuis.api.plugin.KijkbuisPlugin;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginContext;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.scheduler.Scheduler;
import nl.noahvanderaa.kijkbuis.api.ui.AdminPage;
import nl.noahvanderaa.kijkbuis.api.ui.FormHandler;
import nl.noahvanderaa.kijkbuis.api.ui.PageProvider;
import nl.noahvanderaa.kijkbuis.api.ui.SectionProvider;
import nl.noahvanderaa.kijkbuis.api.ui.Slot;
import nl.noahvanderaa.kijkbuis.api.ui.UiManager;
import nl.noahvanderaa.kijkbuis.api.user.Users;
import nl.noahvanderaa.kijkbuis.database.Database;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A plugin Kijkbuis loaded, which is also what the plugin gets as its context.
 */
public final class LoadedPlugin implements PluginContext {

    private static final Logger LOGGER = LogManager.getLogger(LoadedPlugin.class);
    private static final int MAX_CONNECTIONS = 4;
    /**
     * How long the plugin's running tasks get to finish when it's disabled
     */
    private static final Duration TASK_TIMEOUT = Duration.ofSeconds(5);

    private final PluginInfo info;
    private final Icon icon;
    private final PluginClassLoader classLoader;
    private final KijkbuisPlugin instance;
    private final Path directory;
    private final Guide guide;
    private final Users users;
    private final System.Logger logger;
    private final EventManager events;
    private final UiManager ui;
    private final PluginScheduler scheduler;
    /**
     * Only opened once the plugin uses it, so plugins without a database don't get one
     */
    private HikariDataSource database = null;
    private boolean closed = false;

    /**
     * @param icon      the plugin's icon, if it has one Kijkbuis can show
     * @param directory where the plugin's database goes
     * @param timer     the timer of the plugin's scheduler, which all plugins share
     */
    LoadedPlugin(@NotNull PluginInfo info, @Nullable Icon icon, @NotNull PluginClassLoader classLoader, @NotNull KijkbuisPlugin instance,
                 @NotNull Path directory, @NotNull EventBus eventBus, @NotNull UiRegistry ui, @NotNull Guide guide, @NotNull Users users,
                 @NotNull ScheduledExecutorService timer) {
        this.info = info;
        this.icon = icon;
        this.classLoader = classLoader;
        this.instance = instance;
        this.directory = directory;
        this.guide = guide;
        this.users = users;
        this.logger = System.getLogger(info.name());
        this.events = new EventManager() {
            @Override
            public void register(@NotNull Listener listener) {
                eventBus.register(info, listener);
            }

            @Override
            public void unregister(@NotNull Listener listener) {
                eventBus.unregister(listener);
            }
        };
        this.ui = new UiManager() {
            @Override
            public void register(@NotNull Slot slot, @NotNull SectionProvider provider) {
                ui.register(info, slot, provider);
            }

            @Override
            public void handleForm(@NotNull String action, @NotNull FormHandler handler) {
                ui.handleForm(info, action, handler);
            }

            @Override
            @NotNull
            public AdminPage registerPage(@NotNull String id, @NotNull String title, @NotNull PageProvider provider) {
                ui.registerPage(info, null, id, title, provider);
                return new AdminPage() {
                    @Override
                    @NotNull
                    public AdminPage subPage(@NotNull String subPageId, @NotNull String subPageTitle, @NotNull PageProvider subPageProvider) {
                        ui.registerPage(info, id, subPageId, subPageTitle, subPageProvider);
                        return this;
                    }
                };
            }
        };
        this.scheduler = new PluginScheduler(info, timer);
    }

    @Override
    @NotNull
    public PluginInfo info() {
        return this.info;
    }

    @Override
    @NotNull
    public System.Logger logger() {
        return this.logger;
    }

    @Override
    @NotNull
    public EventManager events() {
        return this.events;
    }

    @Override
    @NotNull
    public synchronized DataSource database() {
        if (this.closed) {
            throw new IllegalStateException(this.info.name() + " is disabled");
        }
        if (this.database == null) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            this.database = Database.open(this.directory.resolve(this.info.id() + ".db"), "plugin-" + this.info.id(), MAX_CONNECTIONS);
        }
        return this.database;
    }

    @Override
    @NotNull
    public Guide guide() {
        return this.guide;
    }

    @Override
    @NotNull
    public Users users() {
        return this.users;
    }

    @Override
    @NotNull
    public UiManager ui() {
        return this.ui;
    }

    @Override
    @NotNull
    public Scheduler scheduler() {
        return this.scheduler;
    }

    /**
     * @return the plugin's icon, or null if it has none Kijkbuis can show
     */
    @Nullable
    public Icon icon() {
        return this.icon;
    }

    /**
     * @return the plugin's main class
     */
    @NotNull
    public KijkbuisPlugin instance() {
        return this.instance;
    }

    /**
     * Cancels the plugin's tasks, and gives the ones that are running a few seconds to finish.
     */
    void cancelTasks() {
        this.scheduler.close(TASK_TIMEOUT);
    }

    /**
     * Closes the plugin's database and class loader, once it's disabled. Its tasks are cancelled first, if they
     * weren't already.
     */
    void close() {
        // Without holding the lock, which tasks that are finishing may need to get the database
        this.cancelTasks();
        synchronized (this) {
            this.closed = true;
            if (this.database != null) {
                this.database.close();
            }
            try {
                this.classLoader.close();
            } catch (IOException ex) {
                LOGGER.warn("Failed to close the class loader of {}", this.info.name(), ex);
            }
        }
    }

    /**
     * A plugin's icon, from its jar.
     *
     * @param contentType its media type, such as image/svg+xml
     */
    public record Icon(@NotNull String contentType, byte @NotNull [] data) {

        /**
         * @return something that changes when the icon does, so browsers can keep it for good
         */
        @NotNull
        public String version() {
            return Integer.toHexString(Arrays.hashCode(this.data));
        }

    }

}
