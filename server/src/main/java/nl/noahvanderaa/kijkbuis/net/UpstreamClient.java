package nl.noahvanderaa.kijkbuis.net;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Downloads playlists and EPGs from upstream servers.
 * <p>
 * HttpClient's request timeout only covers waiting for the response headers, not reading the body. Downloads here
 * have a deadline that covers the body too, and a size limit, so a stalled or endless response (such as a stream URL
 * entered as a playlist URL) can't block a refresh forever.
 */
public class UpstreamClient implements AutoCloseable {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("Upstream download watchdog").daemon().factory()
    );

    /**
     * Starts downloading a URL. The returned download must be closed.
     *
     * @param headers  extra request headers
     * @param deadline how long the whole download may take, including reading the body
     * @param maxBytes how large the body may be
     */
    @NotNull
    public Download download(@NotNull String url, @NotNull Map<String, String> headers, @NotNull Duration deadline, long maxBytes) throws IOException {
        Instant expiresAt = Instant.now().plus(deadline);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(deadline)
                .header("User-Agent", "Kijkbuis")
                .GET();
        headers.forEach(request::header);

        HttpResponse<InputStream> response;
        try {
            response = this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("The download was interrupted");
        }

        GuardedInputStream body = new GuardedInputStream(response.body(), maxBytes);
        long remaining = Math.max(0, Duration.between(Instant.now(), expiresAt).toMillis());
        ScheduledFuture<?> timeout = this.watchdog.schedule(body::expire, remaining, TimeUnit.MILLISECONDS);
        return new Download(response.statusCode(), response.headers(), body, timeout);
    }

    @Override
    public void close() {
        this.watchdog.shutdownNow();
        this.httpClient.close();
    }

    /**
     * Describes why a download failed, for admins. Some exceptions have no message of their own, but their cause
     * does.
     */
    @NotNull
    public static String describe(@NotNull Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
        }
        return ex.getClass().getSimpleName();
    }

    /**
     * @param status  the HTTP status code
     * @param headers the response headers
     * @param body    the response body, which fails once the deadline passes or it gets too large
     */
    public record Download(int status, @NotNull HttpHeaders headers, @NotNull InputStream body,
                           @NotNull ScheduledFuture<?> timeout) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            this.timeout.cancel(false);
            this.body.close();
        }

    }

    private static final class GuardedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long bytesRead = 0;
        private volatile boolean expired = false;

        private GuardedInputStream(@NotNull InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        /**
         * Aborts the download. Closing the underlying stream wakes up a read that's waiting for data.
         */
        private void expire() {
            this.expired = true;
            try {
                this.in.close();
            } catch (IOException ignored) {
                // It's being aborted anyway
            }
        }

        @Override
        public int read() throws IOException {
            this.checkExpired();
            int value;
            try {
                value = super.read();
            } catch (IOException ex) {
                // An aborted read fails because the stream was closed, report why it was closed instead
                this.checkExpired();
                throw ex;
            }
            this.count(value == -1 ? -1 : 1);
            return value;
        }

        @Override
        public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
            this.checkExpired();
            int read;
            try {
                read = super.read(buffer, offset, length);
            } catch (IOException ex) {
                // An aborted read fails because the stream was closed, report why it was closed instead
                this.checkExpired();
                throw ex;
            }
            this.count(read);
            return read;
        }

        private void count(int read) throws IOException {
            // Checked again, since an aborted read may also look like the end of the body
            this.checkExpired();
            if (read > 0) {
                this.bytesRead += read;
                if (this.bytesRead > this.maxBytes) {
                    throw new IOException("The download is larger than " + (this.maxBytes / (1024 * 1024)) + " MB");
                }
            }
        }

        private void checkExpired() throws IOException {
            if (this.expired) {
                throw new IOException("The download took too long");
            }
        }

    }

}
