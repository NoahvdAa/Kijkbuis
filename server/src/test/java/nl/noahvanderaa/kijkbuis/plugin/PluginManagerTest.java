package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3, 4};

    @TempDir
    Path tempDir;

    @Test
    void runsPluginsMadeForTheSameMajorVersion() {
        assertTrue(PluginManager.supports("1.2.0", "1.2"));
        assertTrue(PluginManager.supports("1.2.3", "1.2"));
        assertTrue(PluginManager.supports("1.4.0", "1.2"));
        assertTrue(PluginManager.supports("1.2.0-SNAPSHOT", "1.2"));
        assertTrue(PluginManager.supports("1.2.0", "1.2.0"));
    }

    @Test
    void doesNotRunPluginsMadeForNewerOrOtherMajorVersions() {
        assertFalse(PluginManager.supports("1.1.0", "1.2"));
        assertFalse(PluginManager.supports("2.0.0", "1.2"));
        assertFalse(PluginManager.supports("1.2.0", "2.0"));
        assertFalse(PluginManager.supports("1.2.0", "one"));
        assertFalse(PluginManager.supports("1.2.0", "1"));
    }

    @Test
    void readsIconsFromTheJar() throws Exception {
        Path jar = this.jar(Map.of("icon.png", PNG, "images/icon.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)));

        LoadedPlugin.Icon png = PluginManager.readIcon(jar, info("icon.png"));
        assertNotNull(png);
        assertEquals("image/png", png.contentType());
        assertTrue(Arrays.equals(PNG, png.data()));

        LoadedPlugin.Icon svg = PluginManager.readIcon(jar, info("/images/icon.svg"));
        assertNotNull(svg, "A leading slash is fine");
        assertEquals("image/svg+xml", svg.contentType());

        assertNull(PluginManager.readIcon(jar, info(null)), "Plugins don't need an icon");
    }

    @Test
    void leavesOutIconsItCantUse() throws Exception {
        byte[] huge = Arrays.copyOf(PNG, 1024 * 1024 + 1);
        Path jar = this.jar(Map.of("notes.txt", "Not an image".getBytes(StandardCharsets.UTF_8), "huge.png", huge));

        assertNull(PluginManager.readIcon(jar, info("missing.png")));
        assertNull(PluginManager.readIcon(jar, info("notes.txt")));
        assertNull(PluginManager.readIcon(jar, info("huge.png")));
    }

    @NotNull
    private Path jar(@NotNull Map<String, byte[]> files) throws IOException {
        Path jar = this.tempDir.resolve("plugin.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                out.putNextEntry(new JarEntry(file.getKey()));
                out.write(file.getValue());
            }
        }
        return jar;
    }

    @NotNull
    private static PluginInfo info(@Nullable String icon) {
        return new PluginInfo("plugin", "Plugin", "1.0.0", "Plugin", "0.0", null, icon);
    }

}
