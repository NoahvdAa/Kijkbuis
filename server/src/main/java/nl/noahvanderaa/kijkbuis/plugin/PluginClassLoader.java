package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.plugin.KijkbuisPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Loads a plugin's classes from its jar. Plugins only see the JDK and the Kijkbuis API, not how Kijkbuis works inside
 * or the libraries it uses. So plugins can't come to depend on those, and can bundle whatever libraries they like.
 */
final class PluginClassLoader extends URLClassLoader {

    private static final String API_PACKAGE = "nl.noahvanderaa.kijkbuis.api.";

    static {
        ClassLoader.registerAsParallelCapable();
    }

    PluginClassLoader(@NotNull String pluginId, @NotNull Path jar) throws MalformedURLException {
        super("plugin-" + pluginId, new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // The API always comes from Kijkbuis, even if a plugin bundles it, so they agree on what its types are
        if (name.startsWith(API_PACKAGE)) {
            return KijkbuisPlugin.class.getClassLoader().loadClass(name);
        }
        return super.loadClass(name, resolve);
    }

}
