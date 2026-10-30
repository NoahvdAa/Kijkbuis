package nl.noahvanderaa.kijkbuis.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.noahvdaa.schemashift.SchemaShift;
import nl.noahvanderaa.kijkbuis.api.epg.Guide;
import nl.noahvanderaa.kijkbuis.api.plugin.KijkbuisPlugin;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.user.Users;
import nl.noahvanderaa.kijkbuis.channel.LogoCache;
import nl.noahvanderaa.kijkbuis.database.ScriptMigration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads the plugins in the plugins directory. A plugin is a jar with a kijkbuis-plugin.json in it, which has the
 * fields of {@link PluginInfo}. Jars that aren't plugins Kijkbuis can load are skipped, so Kijkbuis still starts.
 * <p>
 * Every plugin gets a directory of its own next to the jars, named after its ID, which holds its database.
 */
public class PluginManager {

    public static final String INFO_FILE = "kijkbuis-plugin.json";
    private static final int MAX_ICON_BYTES = 1024 * 1024;
    private static final String MIGRATIONS_DIRECTORY = "migrations/";
    private static final Logger LOGGER = LogManager.getLogger(PluginManager.class);
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    /**
     * A version's major and minor number, followed by anything such as a patch number or -SNAPSHOT
     */
    private static final Pattern VERSION = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})(?:[.-].*)?");

    private final Path directory;
    private final String kijkbuisVersion;
    private final EventBus eventBus;
    private final UiRegistry ui;
    private final Guide guide;
    private final Users users;
    /**
     * Decides when plugins' tasks are due, see {@link PluginScheduler}
     */
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("plugin-timer").daemon().factory());
    /**
     * The enabled plugins, in the order they were enabled
     */
    private final List<LoadedPlugin> plugins = new ArrayList<>();

    public PluginManager(@NotNull Path directory, @NotNull String kijkbuisVersion, @NotNull EventBus eventBus, @NotNull UiRegistry ui,
                         @NotNull Guide guide, @NotNull Users users) {
        this.directory = directory;
        this.kijkbuisVersion = kijkbuisVersion;
        this.eventBus = eventBus;
        this.ui = ui;
        this.guide = guide;
        this.users = users;
        // Cancelled tasks would otherwise stay in the timer's queue until they would have been due
        this.timer.setRemoveOnCancelPolicy(true);
    }

    /**
     * Loads and enables every plugin in the directory, in alphabetical order. Creates the directory if it doesn't exist
     * yet, so it's clear where plugins go.
     */
    public synchronized void enableAll() throws IOException {
        Files.createDirectories(this.directory);
        List<Path> jars;
        try (Stream<Path> files = Files.list(this.directory)) {
            jars = files.filter((file) -> file.getFileName().toString().endsWith(".jar") && Files.isRegularFile(file)).sorted().toList();
        }
        for (Path jar : jars) {
            this.enable(jar);
        }
    }

    /**
     * Disables every plugin, the last one enabled first, when Kijkbuis stops.
     */
    public synchronized void disableAll() {
        for (LoadedPlugin plugin : this.plugins.reversed()) {
            // First, so onDisable doesn't have to worry about tasks that are still running
            plugin.cancelTasks();
            try {
                plugin.instance().onDisable();
            } catch (Exception | LinkageError ex) {
                LOGGER.error("{} failed to disable", plugin.info().name(), ex);
            }
            this.eventBus.unregisterAll(plugin.info());
            this.ui.unregisterAll(plugin.info());
            plugin.close();
            LOGGER.info("Disabled {}", plugin.info().name());
        }
        this.plugins.clear();
        this.timer.shutdownNow();
    }

    /**
     * @return the enabled plugins, in the order they were enabled
     */
    @NotNull
    public synchronized List<LoadedPlugin> plugins() {
        return List.copyOf(this.plugins);
    }

    /**
     * @return the enabled plugin with the ID, or null if there's none
     */
    @Nullable
    public synchronized LoadedPlugin find(@NotNull String id) {
        return this.plugins.stream().filter((plugin) -> plugin.info().id().equals(id)).findFirst().orElse(null);
    }

    private void enable(@NotNull Path jar) {
        PluginInfo info;
        try {
            info = this.readInfo(jar);
        } catch (InvalidPluginException ex) {
            LOGGER.error("Skipping plugin {}: {}", jar.getFileName(), ex.getMessage());
            return;
        }
        if (this.plugins.stream().anyMatch((plugin) -> plugin.info().id().equals(info.id()))) {
            LOGGER.error("Skipping plugin {}: a plugin with ID {} is enabled already", jar.getFileName(), info.id());
            return;
        }

        PluginClassLoader classLoader = null;
        LoadedPlugin plugin = null;
        try {
            classLoader = new PluginClassLoader(info.id(), jar);
            KijkbuisPlugin instance = instantiate(info, classLoader);
            plugin = new LoadedPlugin(info, readIcon(jar, info), classLoader, instance, this.directory.resolve(info.id()), this.eventBus, this.ui, this.guide,
                    this.users, this.timer);
            migrate(plugin, jar);
            instance.onEnable(plugin);
            this.plugins.add(plugin);
            LOGGER.info("Enabled {} {}", info.name(), info.version());
        } catch (InvalidPluginException ex) {
            LOGGER.error("Skipping plugin {}: {}", jar.getFileName(), ex.getMessage());
            close(plugin, classLoader);
        } catch (Exception | LinkageError ex) {
            LOGGER.error("Failed to enable {} {}", info.name(), info.version(), ex);
            this.eventBus.unregisterAll(info);
            this.ui.unregisterAll(info);
            close(plugin, classLoader);
        }
    }

    /**
     * Runs the migrations in the plugin's jar that didn't run on its database yet: the SQL scripts in its migrations
     * directory, in alphabetical order. They run in one transaction, so if one fails, none of them happened.
     */
    private static void migrate(@NotNull LoadedPlugin plugin, @NotNull Path jar) throws IOException, SQLException {
        List<ScriptMigration> migrations = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(file.entries())) {
                String name = entry.getName();
                if (name.startsWith(MIGRATIONS_DIRECTORY) && name.endsWith(".sql") && name.indexOf('/', MIGRATIONS_DIRECTORY.length()) == -1) {
                    try (InputStream in = file.getInputStream(entry)) {
                        migrations.add(new ScriptMigration(name.substring(MIGRATIONS_DIRECTORY.length()), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        if (migrations.isEmpty()) {
            return;
        }
        // The migration library only runs migrations that come after the last one it ran, in the order they're registered
        migrations.sort(Comparator.comparing(ScriptMigration::id));

        try (Connection connection = plugin.database().getConnection()) {
            SchemaShift migrator = SchemaShift.with(connection);
            migrator.registerMigrations(List.copyOf(migrations));
            connection.setAutoCommit(false);
            boolean applied;
            try {
                applied = migrator.migrateLatest();
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
            if (applied) {
                LOGGER.info("Applied the migrations of {}", plugin.info().name());
            }
        }
    }

    @NotNull
    private PluginInfo readInfo(@NotNull Path jar) throws InvalidPluginException {
        JsonObject json;
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry(INFO_FILE);
            if (entry == null) {
                throw new InvalidPluginException("it has no " + INFO_FILE);
            }
            try (Reader reader = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException ex) {
            throw new InvalidPluginException("it can't be read: " + ex.getMessage());
        } catch (JsonParseException | IllegalStateException ex) {
            throw new InvalidPluginException("its " + INFO_FILE + " isn't a JSON object");
        }

        PluginInfo info = new PluginInfo(string(json, "id"), string(json, "name"), string(json, "version"), string(json, "main"), string(json, "apiVersion"),
                optionalString(json, "description"), optionalString(json, "icon"));
        if (!ID.matcher(info.id()).matches()) {
            throw new InvalidPluginException("its ID must be lowercase letters, digits and underscores, starting with a letter, rather than " + info.id());
        }
        if (!VERSION.matcher(info.apiVersion()).matches()) {
            throw new InvalidPluginException("its apiVersion must be a Kijkbuis version such as 1.2, rather than " + info.apiVersion());
        }
        if (!supports(this.kijkbuisVersion, info.apiVersion())) {
            throw new InvalidPluginException("it's made for Kijkbuis " + info.apiVersion() + ", this is Kijkbuis " + this.kijkbuisVersion);
        }
        return info;
    }

    /**
     * @return whether a plugin made for one Kijkbuis version runs on another. That takes the same major version, and
     * the same minor version or a newer one, since only major versions change the API in ways that break plugins.
     */
    static boolean supports(@NotNull String kijkbuisVersion, @NotNull String apiVersion) {
        Matcher kijkbuis = VERSION.matcher(kijkbuisVersion);
        Matcher api = VERSION.matcher(apiVersion);
        if (!kijkbuis.matches() || !api.matches()) {
            return false;
        }
        return Integer.parseInt(kijkbuis.group(1)) == Integer.parseInt(api.group(1))
                && Integer.parseInt(kijkbuis.group(2)) >= Integer.parseInt(api.group(2));
    }

    @NotNull
    private static KijkbuisPlugin instantiate(@NotNull PluginInfo info, @NotNull ClassLoader classLoader) throws Exception {
        Class<?> main;
        try {
            main = Class.forName(info.main(), true, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new InvalidPluginException("its main class " + info.main() + " doesn't exist");
        }
        if (!KijkbuisPlugin.class.isAssignableFrom(main)) {
            throw new InvalidPluginException("its main class " + info.main() + " doesn't implement KijkbuisPlugin");
        }

        try {
            return main.asSubclass(KijkbuisPlugin.class).getConstructor().newInstance();
        } catch (NoSuchMethodException ex) {
            throw new InvalidPluginException("its main class " + info.main() + " has no public constructor without parameters");
        }
    }

    @NotNull
    private static String string(@NotNull JsonObject json, @NotNull String key) throws InvalidPluginException {
        String value = optionalString(json, key);
        if (value == null) {
            throw new InvalidPluginException("its " + INFO_FILE + " has no " + key);
        }
        return value;
    }

    /**
     * @return the text under the key, or null if there's none
     * @throws InvalidPluginException if there's something other than text under the key
     */
    @Nullable
    private static String optionalString(@NotNull JsonObject json, @NotNull String key) throws InvalidPluginException {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new InvalidPluginException("the " + key + " in its " + INFO_FILE + " must be text");
        }
        return value.getAsString().isBlank() ? null : value.getAsString();
    }

    /**
     * Reads the plugin's icon from its jar. A plugin with an icon that can't be used still works, so that's only
     * logged.
     *
     * @return the icon, or null if the plugin has none or it can't be used
     */
    @Nullable
    static LoadedPlugin.Icon readIcon(@NotNull Path jar, @NotNull PluginInfo info) {
        if (info.icon() == null) {
            return null;
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry(info.icon().startsWith("/") ? info.icon().substring(1) : info.icon());
            if (entry == null || entry.isDirectory()) {
                LOGGER.warn("{} has no icon at {} in its jar, showing it without one", info.name(), info.icon());
                return null;
            }
            byte[] data;
            try (InputStream in = file.getInputStream(entry)) {
                data = in.readNBytes(MAX_ICON_BYTES + 1);
            }
            if (data.length > MAX_ICON_BYTES) {
                LOGGER.warn("The icon of {} is larger than 1 MB, showing it without one", info.name());
                return null;
            }
            String contentType = LogoCache.imageType(null, data);
            if (contentType == null) {
                LOGGER.warn("The icon of {} isn't an image, showing it without one", info.name());
                return null;
            }
            return new LoadedPlugin.Icon(contentType, data);
        } catch (IOException ex) {
            LOGGER.warn("Failed to read the icon of {}, showing it without one", info.name(), ex);
            return null;
        }
    }

    /**
     * Closes what was opened for a plugin that failed to enable.
     */
    private static void close(@Nullable LoadedPlugin plugin, @Nullable PluginClassLoader classLoader) {
        if (plugin != null) {
            plugin.close();
        } else if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException ex) {
                LOGGER.warn("Failed to close the class loader of {}", classLoader.getName(), ex);
            }
        }
    }

    /**
     * Thrown when a jar isn't a plugin Kijkbuis can load. The message says why, after "Skipping plugin x.jar: ".
     */
    private static final class InvalidPluginException extends Exception {

        InvalidPluginException(@NotNull String message) {
            super(message);
        }

    }

}
