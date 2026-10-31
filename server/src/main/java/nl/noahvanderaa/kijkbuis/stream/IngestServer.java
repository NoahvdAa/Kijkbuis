package nl.noahvanderaa.kijkbuis.stream;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Receives the files ffmpeg pushes over HTTP and hands them to their stream, so segments never touch the disk.
 * Only listens on the loopback interface, and each stream's URLs contain a secret token.
 */
class IngestServer {

    private static final Logger LOGGER = LogManager.getLogger(IngestServer.class);
    private static final Pattern FILE_NAME = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}$");
    private static final int MAX_FILE_SIZE = 64 * 1024 * 1024;

    private final StreamManager streamManager;
    private Undertow server = null;
    private String baseUrl = null;

    IngestServer(@NotNull StreamManager streamManager) {
        this.streamManager = streamManager;
    }

    void start() {
        // Port 0 picks any free port, ffmpeg is told which one
        this.server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new BlockingHandler(this::handleRequest))
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, (long) MAX_FILE_SIZE)
                .setServerOption(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE, (long) MAX_FILE_SIZE)
                .build();
        this.server.start();

        InetSocketAddress address = (InetSocketAddress) this.server.getListenerInfo().getFirst().getAddress();
        this.baseUrl = "http://127.0.0.1:" + address.getPort() + "/";
        LOGGER.info("Receiving streams from ffmpeg on {}", this.baseUrl);
    }

    void stop() {
        if (this.server != null) {
            this.server.stop();
        }
    }

    /**
     * @return the URL ffmpeg should push the stream's files to, ending with a slash
     */
    @NotNull
    URI ingestUri(@NotNull Stream stream) {
        return URI.create(this.baseUrl + stream.id() + "/" + stream.ingestToken() + "/");
    }

    private void handleRequest(@NotNull HttpServerExchange exchange) throws IOException {
        // Paths look like /<stream id>/<ingest token>/<file name>
        String[] parts = exchange.getRequestPath().split("/");
        Stream stream = parts.length == 4 ? this.streamManager.findForIngest(parts[1], parts[2]) : null;
        if (stream == null || !FILE_NAME.matcher(parts[3]).matches()) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            return;
        }
        String fileName = parts[3];

        if (exchange.getRequestMethod().equals(Methods.PUT) || exchange.getRequestMethod().equals(Methods.POST)) {
            byte[] data = exchange.getInputStream().readNBytes(MAX_FILE_SIZE + 1);
            if (data.length > MAX_FILE_SIZE) {
                LOGGER.warn("ffmpeg tried to upload a file over {} bytes for {}", MAX_FILE_SIZE, stream.channel().name());
                exchange.setStatusCode(StatusCodes.REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            stream.putFile(fileName, data);
            exchange.setStatusCode(StatusCodes.CREATED);
        } else if (exchange.getRequestMethod().equals(Methods.DELETE)) {
            stream.deleteFile(fileName);
            exchange.setStatusCode(StatusCodes.NO_CONTENT);
        } else {
            exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        }
    }

}
