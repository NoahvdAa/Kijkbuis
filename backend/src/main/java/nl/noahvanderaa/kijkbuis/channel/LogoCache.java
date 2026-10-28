package nl.noahvanderaa.kijkbuis.channel;

import nl.noahvanderaa.kijkbuis.net.UpstreamClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches channel logos for browsers, which can't always reach the hosts playlists point them at (such as a hostname
 * on the provider's network). Logos are kept in memory for a day, and failures for a few minutes, so the logo's host
 * isn't asked again for every page view.
 * <p>
 * The URLs come from playlists, so this only ever hands out images, never whatever else a URL might return.
 */
public class LogoCache {

    private static final Logger LOGGER = LogManager.getLogger(LogoCache.class);
    private static final long MAX_LOGO_BYTES = 2 * 1024 * 1024;
    /**
     * Logos are small, so this fits thousands. The least recently used ones go first when it's full.
     */
    private static final long MAX_CACHED_BYTES = 64 * 1024 * 1024;
    private static final Duration DOWNLOAD_DEADLINE = Duration.ofSeconds(10);
    private static final Duration KEEP = Duration.ofDays(1);
    private static final Duration KEEP_FAILURE = Duration.ofMinutes(5);

    private final UpstreamClient upstream;
    /**
     * By URL, in order of use: the least recently used first
     */
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>(64, 0.75f, true);
    private long cachedBytes = 0;
    /**
     * Logos being downloaded, so requests for the same logo at the same time share one download
     */
    private final Map<String, CompletableFuture<Entry>> downloads = new ConcurrentHashMap<>();

    public LogoCache(@NotNull UpstreamClient upstream) {
        this.upstream = upstream;
    }

    /**
     * @return the logo at the URL, or null if it couldn't be downloaded or isn't an image
     */
    @Nullable
    public Logo get(@NotNull String url) {
        synchronized (this.cache) {
            Entry entry = this.cache.get(url);
            if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
                return entry.logo();
            }
        }

        CompletableFuture<Entry> download = new CompletableFuture<>();
        CompletableFuture<Entry> running = this.downloads.putIfAbsent(url, download);
        if (running != null) {
            return running.join().logo();
        }
        try {
            Entry entry = this.download(url);
            this.store(url, entry);
            download.complete(entry);
            return entry.logo();
        } finally {
            // Whatever happened, don't leave requests waiting for this download
            download.complete(new Entry(null, Instant.now()));
            this.downloads.remove(url, download);
        }
    }

    @NotNull
    private Entry download(@NotNull String url) {
        Instant now = Instant.now();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return new Entry(null, now.plus(KEEP_FAILURE));
        }

        try (UpstreamClient.Download download = this.upstream.download(url, Map.of("Accept", "image/*"), DOWNLOAD_DEADLINE, MAX_LOGO_BYTES)) {
            if (download.status() != 200) {
                throw new IOException("The logo URL responded with HTTP " + download.status());
            }
            byte[] data = download.body().readAllBytes();
            String type = imageType(download.headers().firstValue("Content-Type").orElse(null), data);
            if (type == null) {
                throw new IOException("The logo URL didn't respond with an image");
            }
            return new Entry(new Logo(data, type), now.plus(KEEP));
        } catch (IOException | RuntimeException ex) {
            // Not the URL, which can contain provider credentials
            LOGGER.debug("Failed to load a channel logo: {}", UpstreamClient.describe(ex));
            return new Entry(null, now.plus(KEEP_FAILURE));
        }
    }

    private void store(@NotNull String url, @NotNull Entry entry) {
        synchronized (this.cache) {
            Entry previous = this.cache.put(url, entry);
            this.cachedBytes += entry.size() - (previous != null ? previous.size() : 0);

            Iterator<Entry> leastRecentlyUsed = this.cache.values().iterator();
            while (this.cachedBytes > MAX_CACHED_BYTES && leastRecentlyUsed.hasNext()) {
                this.cachedBytes -= leastRecentlyUsed.next().size();
                leastRecentlyUsed.remove();
            }
        }
    }

    /**
     * @param declared the Content-Type the server sent, if any
     * @return the image's media type, or null if it isn't an image
     */
    @Nullable
    static String imageType(@Nullable String declared, byte @NotNull [] data) {
        if (declared != null) {
            String type = declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (type.startsWith("image/")) {
                return type;
            }
        }

        // Some servers don't say, or say application/octet-stream, so recognise the usual formats by how they start
        if (startsWith(data, 0x89, 'P', 'N', 'G')) {
            return "image/png";
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(data, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (startsWith(data, 'R', 'I', 'F', 'F') && data.length >= 12 && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return "image/webp";
        }
        if (startsWith(data, 0, 0, 1, 0)) {
            return "image/x-icon";
        }
        String text = new String(data, 0, Math.min(data.length, 512), StandardCharsets.ISO_8859_1).stripLeading();
        if (text.startsWith("<svg") || (text.startsWith("<?xml") && text.contains("<svg"))) {
            return "image/svg+xml";
        }
        return null;
    }

    private static boolean startsWith(byte @NotNull [] data, int @NotNull ... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param data        the image
     * @param contentType its media type, such as image/png
     */
    public record Logo(byte @NotNull [] data, @NotNull String contentType) {
    }

    /**
     * @param logo      the logo, or null if it couldn't be loaded
     * @param expiresAt when to try the URL again
     */
    private record Entry(@Nullable Logo logo, @NotNull Instant expiresAt) {

        long size() {
            return this.logo != null ? this.logo.data().length : 0;
        }

    }

}
