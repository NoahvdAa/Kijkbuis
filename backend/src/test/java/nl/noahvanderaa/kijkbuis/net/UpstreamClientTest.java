package nl.noahvanderaa.kijkbuis.net;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamClientTest {

    private final UpstreamClient client = new UpstreamClient();
    private ServerSocket server;

    @BeforeEach
    void openServer() throws IOException {
        this.server = new ServerSocket(0);
    }

    @AfterEach
    void close() throws IOException {
        this.client.close();
        this.server.close();
    }

    @Test
    void downloadsBodies() throws Exception {
        this.respond("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello", false);
        try (UpstreamClient.Download download = this.client.download(this.url(), Map.of(), Duration.ofSeconds(5), 1024)) {
            assertEquals(200, download.status());
            assertEquals("hello", new String(download.body().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void abortsBodiesThatStall() throws Exception {
        // Headers arrive right away, the body never finishes
        this.respond("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n#EXTM3U\n", true);
        Instant start = Instant.now();
        try (UpstreamClient.Download download = this.client.download(this.url(), Map.of(), Duration.ofSeconds(1), 1024 * 1024)) {
            IOException ex = assertThrows(IOException.class, () -> download.body().readAllBytes());
            assertEquals("The download took too long", ex.getMessage());
        }
        assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(5)) < 0, "The download should be aborted at the deadline");
    }

    @Test
    void abortsBodiesThatAreTooLarge() throws Exception {
        this.respond("HTTP/1.1 200 OK\r\nContent-Length: 2048\r\n\r\n" + "x".repeat(2048), false);
        try (UpstreamClient.Download download = this.client.download(this.url(), Map.of(), Duration.ofSeconds(5), 1024)) {
            assertThrows(IOException.class, () -> download.body().readAllBytes());
        }
    }

    @NotNull
    private String url() {
        return "http://127.0.0.1:" + this.server.getLocalPort() + "/";
    }

    /**
     * Answers one request with the given raw response.
     *
     * @param stall whether to keep the connection open afterwards without sending anything more
     */
    private void respond(@NotNull String response, boolean stall) {
        Thread.ofVirtual().start(() -> {
            try (Socket socket = this.server.accept()) {
                InputStream in = socket.getInputStream();
                in.read(new byte[4096]);
                OutputStream out = socket.getOutputStream();
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (stall) {
                    Thread.sleep(30_000);
                }
            } catch (IOException | InterruptedException ignored) {
                // The test is over
            }
        });
    }

}
