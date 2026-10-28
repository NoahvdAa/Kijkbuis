package nl.noahvanderaa.kijkbuis;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.auth.OidcClient;
import nl.noahvanderaa.kijkbuis.auth.SessionManager;
import nl.noahvanderaa.kijkbuis.channel.ChannelManager;
import nl.noahvanderaa.kijkbuis.channel.LogoCache;
import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import nl.noahvanderaa.kijkbuis.database.Database;
import nl.noahvanderaa.kijkbuis.epg.EpgManager;
import nl.noahvanderaa.kijkbuis.http.ErrorHandler;
import nl.noahvanderaa.kijkbuis.http.FrontendHandler;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.auth.CallbackHandler;
import nl.noahvanderaa.kijkbuis.http.handler.auth.LoginHandler;
import nl.noahvanderaa.kijkbuis.http.handler.auth.LogoutHandler;
import nl.noahvanderaa.kijkbuis.http.handler.channel.GetChannelHandler;
import nl.noahvanderaa.kijkbuis.http.handler.channel.GetChannelLogoHandler;
import nl.noahvanderaa.kijkbuis.http.handler.channel.GetGuideHandler;
import nl.noahvanderaa.kijkbuis.http.handler.channel.ListChannelsHandler;
import nl.noahvanderaa.kijkbuis.http.handler.channel.UpdateChannelHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.CreatePlaylistHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.DeletePlaylistHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.GetPlaylistHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.ListEpgChannelsHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.ListPlaylistsHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.RefreshPlaylistHandler;
import nl.noahvanderaa.kijkbuis.http.handler.playlist.UpdatePlaylistHandler;
import nl.noahvanderaa.kijkbuis.http.handler.stream.GetStreamFileHandler;
import nl.noahvanderaa.kijkbuis.http.handler.stream.ListProfilesHandler;
import nl.noahvanderaa.kijkbuis.http.handler.stream.ListStreamsHandler;
import nl.noahvanderaa.kijkbuis.http.handler.stream.StartStreamHandler;
import nl.noahvanderaa.kijkbuis.http.handler.stream.StopStreamHandler;
import nl.noahvanderaa.kijkbuis.http.handler.user.GetCurrentUserHandler;
import nl.noahvanderaa.kijkbuis.http.handler.user.ListUsersHandler;
import nl.noahvanderaa.kijkbuis.http.handler.user.SignOutUserHandler;
import nl.noahvanderaa.kijkbuis.net.UpstreamClient;
import nl.noahvanderaa.kijkbuis.playlist.PlaylistManager;
import nl.noahvanderaa.kijkbuis.stream.StreamManager;
import nl.noahvanderaa.kijkbuis.task.CleanupExpiredSessionsTask;
import nl.noahvanderaa.kijkbuis.task.RefreshPlaylistsTask;
import nl.noahvanderaa.kijkbuis.task.StopIdleStreamsTask;
import nl.noahvanderaa.kijkbuis.user.UserManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KijkbuisApp {

    public static final KijkbuisApp INSTANCE = new KijkbuisApp();
    private static final Logger LOGGER = LogManager.getLogger(KijkbuisApp.class);
    private final Database database = new Database();
    private final UpstreamClient upstream = new UpstreamClient();
    private final LogoCache logoCache = new LogoCache(this.upstream);
    private KijkbuisConfig config;
    private UserManager userManager;
    private SessionManager sessionManager;
    private OidcClient oidcClient;
    private ChannelManager channelManager;
    private EpgManager epgManager;
    private PlaylistManager playlistManager;
    private StreamManager streamManager;
    private ScheduledExecutorService scheduler;
    private Undertow server;

    public static void main(String[] args) {
        // Child processes outlive the JVM, so make sure ffmpeg gets stopped
        Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::stop, "Kijkbuis shutdown"));
        try {
            INSTANCE.start(Path.of(args.length > 0 ? args[0] : "kijkbuis.toml"));
        } catch (Exception ex) {
            LOGGER.error("Kijkbuis failed to start", ex);
            System.exit(1);
        }
    }

    void start(@NotNull Path configPath) throws Exception {
        LOGGER.info("This program is licensed under the GNU GPLv3 and comes with ABSOLUTELY NO WARRANTY. See LICENSE for more details.");
        LOGGER.info("Kijkbuis is getting ready...");

        LOGGER.info("Loading config from {}...", configPath.toAbsolutePath());
        this.config = KijkbuisConfig.load(configPath);

        this.database.initialize(Path.of(this.config.database().path()));
        this.userManager = new UserManager(this.database, this.config.auth());
        this.sessionManager = new SessionManager(this.database, this.userManager, this.config.auth().sessionLifetime());
        this.channelManager = new ChannelManager(this.database);
        this.epgManager = new EpgManager(this.database, this.upstream);
        this.playlistManager = new PlaylistManager(this.database, this.channelManager, this.epgManager, this.upstream);
        this.streamManager = new StreamManager(this.config.streaming(), this.config.transcoding());
        this.streamManager.initialize();

        LOGGER.info("Discovering identity provider...");
        this.oidcClient = new OidcClient(this.config.auth(), URI.create(this.config.server().publicUrl() + "/api/auth/callback"));
        this.oidcClient.tryDiscover();

        LOGGER.info("Scheduling repeating tasks...");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(new CleanupExpiredSessionsTask(this), 1L, 60L, TimeUnit.MINUTES);
        this.scheduler.scheduleAtFixedRate(new RefreshPlaylistsTask(this), 0L, 1L, TimeUnit.MINUTES);
        this.scheduler.scheduleAtFixedRate(new StopIdleStreamsTask(this), 1L, 1L, TimeUnit.SECONDS);

        LOGGER.info("Preparing webserver...");
        RoutingHandler routes = Handlers.routing()
                .get("/api/auth/login", new LoginHandler(this))
                .get("/api/auth/callback", new CallbackHandler(this))
                .post("/api/auth/logout", new LogoutHandler(this))
                .get("/api/me", new GetCurrentUserHandler(this))
                .get("/api/playlists", new ListPlaylistsHandler(this))
                .post("/api/playlists", new CreatePlaylistHandler(this))
                .get("/api/playlists/{playlist}", new GetPlaylistHandler(this))
                .add(Methods.PATCH, "/api/playlists/{playlist}", new UpdatePlaylistHandler(this))
                .delete("/api/playlists/{playlist}", new DeletePlaylistHandler(this))
                .post("/api/playlists/{playlist}/refresh", new RefreshPlaylistHandler(this))
                .get("/api/playlists/{playlist}/epg-channels", new ListEpgChannelsHandler(this))
                .get("/api/channels", new ListChannelsHandler(this))
                .get("/api/channels/{channel}", new GetChannelHandler(this))
                .get("/api/channels/{channel}/logo", new GetChannelLogoHandler(this))
                .add(Methods.PATCH, "/api/channels/{channel}", new UpdateChannelHandler(this))
                .get("/api/guide", new GetGuideHandler(this))
                .get("/api/profiles", new ListProfilesHandler(this))
                .get("/api/streams", new ListStreamsHandler(this))
                .post("/api/streams", new StartStreamHandler(this))
                .delete("/api/streams/{stream}", new StopStreamHandler(this))
                .get("/api/users", new ListUsersHandler(this))
                .delete("/api/users/{user}/sessions", new SignOutUserHandler(this))
                .get("/live/{token}/{file}", new GetStreamFileHandler(this))
                .setFallbackHandler(this.fallbackHandler())
                .setInvalidMethodHandler((exchange) -> Responses.error(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Method not allowed"));

        // Stream segments are compressed video already, gzipping them would only cost CPU
        ContentEncodingRepository encodings = new ContentEncodingRepository()
                .addEncodingHandler("gzip", new GzipEncodingProvider(), 50, Predicates.not(Predicates.prefix("/live/")));

        this.server = Undertow.builder()
                .addHttpListener(this.config.server().port(), this.config.server().host())
                // Every request is handled on a worker thread in blocking mode, so handlers can do database and network IO directly
                .setHandler(new BlockingHandler(new EncodingHandler(new ErrorHandler(routes), encodings)))
                .build();
        this.server.start();
        LOGGER.info("Kijkbuis is ready on {}", this.config.server().publicUrl());
    }

    /**
     * @return what handles requests the API doesn't: the frontend if it's served, otherwise a 404
     */
    @NotNull
    private HttpHandler fallbackHandler() {
        HttpHandler notFound = (exchange) -> Responses.error(exchange, StatusCodes.NOT_FOUND, "Not found");
        Path frontendDir = this.config.server().frontendDir();
        if (frontendDir == null) {
            LOGGER.info("Only serving the API, server.frontend-dir is not set");
            return notFound;
        }
        if (!Files.isRegularFile(frontendDir.resolve("index.html"))) {
            LOGGER.warn("Not serving the frontend, {} has no index.html. Check server.frontend-dir in the config.", frontendDir.toAbsolutePath());
            return notFound;
        }
        LOGGER.info("Serving the frontend from {}", frontendDir.toAbsolutePath());
        return new FrontendHandler(frontendDir);
    }

    void stop() {
        if (this.server != null) {
            this.server.stop();
        }
        if (this.scheduler != null) {
            this.scheduler.shutdownNow();
        }
        if (this.playlistManager != null) {
            this.playlistManager.close();
        }
        if (this.epgManager != null) {
            this.epgManager.close();
        }
        if (this.streamManager != null) {
            this.streamManager.close();
        }
        this.upstream.close();
        this.database.close();
    }

    @NotNull
    public KijkbuisConfig config() {
        return this.config;
    }

    @NotNull
    public Database database() {
        return this.database;
    }

    @NotNull
    public UserManager userManager() {
        return this.userManager;
    }

    @NotNull
    public SessionManager sessionManager() {
        return this.sessionManager;
    }

    @NotNull
    public OidcClient oidcClient() {
        return this.oidcClient;
    }

    @NotNull
    public LogoCache logoCache() {
        return this.logoCache;
    }

    @NotNull
    public ChannelManager channelManager() {
        return this.channelManager;
    }

    @NotNull
    public EpgManager epgManager() {
        return this.epgManager;
    }

    @NotNull
    public PlaylistManager playlistManager() {
        return this.playlistManager;
    }

    @NotNull
    public StreamManager streamManager() {
        return this.streamManager;
    }

}
