package nl.noahvanderaa.kijkbuis.api.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * The main class of a plugin, which Kijkbuis creates when it starts. The plugin's kijkbuis-plugin.json names it in
 * {@code main}, and it needs a public constructor without parameters.
 */
public interface KijkbuisPlugin {

    /**
     * Called when Kijkbuis starts, before it handles any requests. This is where a plugin registers its listeners.
     * If it throws, Kijkbuis disables the plugin again and starts without it.
     *
     * @param context what the plugin can do in Kijkbuis, which it can keep for as long as it's enabled
     */
    void onEnable(@NotNull PluginContext context) throws Exception;

    /**
     * Called when Kijkbuis stops, after the last streams stopped. Kijkbuis unregisters the plugin's listeners
     * afterwards.
     */
    default void onDisable() throws Exception {
    }

}
