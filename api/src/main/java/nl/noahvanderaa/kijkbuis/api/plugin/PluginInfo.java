package nl.noahvanderaa.kijkbuis.api.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A plugin's details, from the kijkbuis-plugin.json in its jar.
 *
 * @param id          the plugin's unique ID: lowercase letters, digits and underscores, starting with a letter
 * @param name        the name people see
 * @param version     the plugin's own version
 * @param main        the {@link KijkbuisPlugin} class Kijkbuis creates
 * @param apiVersion  the Kijkbuis version the plugin is made for, such as 1.2. It runs on that version and any newer
 *                    one with the same major version.
 * @param description what the plugin does, in a sentence or two, if it says
 * @param icon        the path of the plugin's icon in its jar, such as icon.svg, if it has one
 */
public record PluginInfo(
        @NotNull String id,
        @NotNull String name,
        @NotNull String version,
        @NotNull String main,
        @NotNull String apiVersion,
        @Nullable String description,
        @Nullable String icon
) {
}
