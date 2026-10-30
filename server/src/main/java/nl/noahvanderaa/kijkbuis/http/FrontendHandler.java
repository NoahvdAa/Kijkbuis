package nl.noahvanderaa.kijkbuis.http;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Serves the built frontend from a directory. Its files are served as they are. Any other path (such as /guide) gets
 * index.html, since the frontend has its own pages, unless it looks like a file or belongs to the API or the streams:
 * those get a JSON 404 like the API's.
 */
public class FrontendHandler implements HttpHandler {

    private static final String INDEX = "/index.html";
    /**
     * Vite puts a hash of their contents in the names of the files in /assets, so those never change
     */
    private static final String ASSETS = "/assets/";

    private final ResourceHandler files;

    /**
     * @param directory the frontend's build output, with index.html in it
     */
    public FrontendHandler(@NotNull Path directory) {
        this.files = new ResourceHandler(new PathResourceManager(directory), this::notFound)
                .setWelcomeFiles("index.html")
                .setDirectoryListingEnabled(false);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRelativePath();
        if (path.equals("/api") || path.startsWith("/api/") || path.startsWith("/live/")) {
            Responses.error(exchange, StatusCodes.NOT_FOUND, "Not found");
            return;
        }

        // Everything else could change with the next version, so browsers have to check for that
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, path.startsWith(ASSETS) ? "public, max-age=31536000, immutable" : "no-cache");
        this.files.handleRequest(exchange);
    }

    /**
     * Called by the resource handler for paths without a file.
     */
    private void notFound(@NotNull HttpServerExchange exchange) throws Exception {
        String path = exchange.getRelativePath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.contains(".")) {
            // A missing file, such as an asset of an older version. Serving the page instead would only confuse.
            exchange.getResponseHeaders().remove(Headers.CACHE_CONTROL);
            Responses.error(exchange, StatusCodes.NOT_FOUND, "Not found");
            return;
        }

        // One of the frontend's pages. index.html has a dot, so this can't loop.
        exchange.setRelativePath(INDEX);
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-cache");
        this.files.handleRequest(exchange);
    }

}
