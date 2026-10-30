package nl.noahvanderaa.kijkbuis.api.plugin;

import nl.noahvanderaa.kijkbuis.api.epg.Guide;
import nl.noahvanderaa.kijkbuis.api.event.EventManager;
import nl.noahvanderaa.kijkbuis.api.scheduler.Scheduler;
import nl.noahvanderaa.kijkbuis.api.ui.UiManager;
import nl.noahvanderaa.kijkbuis.api.user.Users;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;

/**
 * What a plugin can do in Kijkbuis. Every plugin gets its own, in {@link KijkbuisPlugin#onEnable}.
 */
public interface PluginContext {

    /**
     * @return the plugin's details, from its kijkbuis-plugin.json
     */
    @NotNull
    PluginInfo info();

    /**
     * @return the logger for the plugin, which ends up in Kijkbuis' log with the plugin's name
     */
    @NotNull
    System.Logger logger();

    /**
     * @return where the plugin registers its event listeners
     */
    @NotNull
    EventManager events();

    /**
     * @return the plugin's own SQLite database, which only it uses. Its tables are created by the SQL scripts in the
     * migrations directory of the plugin's jar, which Kijkbuis runs before enabling the plugin. The connections come
     * from a small pool, so close them when you're done with them.
     */
    @NotNull
    DataSource database();

    /**
     * @return the programmes from the EPG
     */
    @NotNull
    Guide guide();

    /**
     * @return everyone who ever logged in, such as to find the users whose IDs the plugin stored
     */
    @NotNull
    Users users();

    /**
     * @return where the plugin adds sections to Kijkbuis' pages
     */
    @NotNull
    UiManager ui();

    /**
     * @return what runs the plugin's tasks later, or every so often
     */
    @NotNull
    Scheduler scheduler();

}
