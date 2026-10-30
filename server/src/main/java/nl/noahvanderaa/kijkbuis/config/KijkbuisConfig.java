package nl.noahvanderaa.kijkbuis.config;

import com.google.gson.JsonObject;
import me.lucko.configurate.toml.TOMLConfigurationLoader;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.util.NamingSchemes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigSerializable
public class KijkbuisConfig {

    private static final Logger LOGGER = LogManager.getLogger(KijkbuisConfig.class);
    private static final String DEFAULT_CONFIG_RESOURCE = "kijkbuis.default.toml";
    private static final ObjectMapper.Factory OBJECT_MAPPER_FACTORY = ObjectMapper.factoryBuilder()
            .defaultNamingScheme(NamingSchemes.LOWER_CASE_DASHED)
            .build();
    private static final Pattern PROFILE_ID = Pattern.compile("^(?!auto$)[a-z0-9_-]{1,32}$");

    private ServerConfig server = new ServerConfig();
    private DatabaseConfig database = new DatabaseConfig();
    private AuthConfig auth = new AuthConfig();
    private StreamingConfig streaming = new StreamingConfig();
    private TranscodingConfig transcoding = new TranscodingConfig();

    /**
     * Loads and validates the config at the given path.
     * If there is no config yet, the bundled default config is written there first.
     *
     * @param path the path of the TOML config file
     * @return the loaded config
     * @throws IOException           if the file could not be read or parsed
     * @throws IllegalStateException if the config contains invalid values
     */
    @NotNull
    public static KijkbuisConfig load(@NotNull Path path) throws IOException {
        if (Files.notExists(path)) {
            try (InputStream defaultConfig = KijkbuisConfig.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                Files.copy(Objects.requireNonNull(defaultConfig, "Missing bundled default config"), path);
            }
            LOGGER.warn("No config found, so a default one was created at {}. Fill in the [auth] section and restart Kijkbuis.", path.toAbsolutePath());
        }

        TOMLConfigurationLoader loader = TOMLConfigurationLoader.builder()
                .path(path)
                .defaultOptions((options) -> options.serializers((builder) -> builder.registerAnnotatedObjects(OBJECT_MAPPER_FACTORY)))
                .build();
        KijkbuisConfig config = loader.load().get(KijkbuisConfig.class);
        if (config == null) {
            throw new IOException("Config file " + path + " is empty");
        }

        config.validate();
        return config;
    }

    private void validate() {
        URI publicUrl;
        try {
            publicUrl = URI.create(this.server.publicUrl());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid config: server.public-url is not a valid URL", ex);
        }
        if (!"http".equals(publicUrl.getScheme()) && !"https".equals(publicUrl.getScheme())) {
            throw new IllegalStateException("Invalid config: server.public-url must start with http:// or https://");
        }
        if (this.auth.issuer().isBlank()) {
            throw new IllegalStateException("Invalid config: auth.issuer must be set");
        }
        if (this.auth.clientId().isBlank()) {
            throw new IllegalStateException("Invalid config: auth.client-id must be set");
        }
        if (this.auth.sessionLifetime().isNegative() || this.auth.sessionLifetime().isZero()) {
            throw new IllegalStateException("Invalid config: auth.session-lifetime-hours must be positive");
        }
        if (this.auth.privileges.getOrDefault(Privilege.ADMIN, List.of()).contains(AuthConfig.EVERYONE)) {
            throw new IllegalStateException("Invalid config: auth.privileges.admin can't be given to everyone (\"" + AuthConfig.EVERYONE + "\"), only to groups");
        }
        if (this.streaming.ffmpegPath().isBlank()) {
            throw new IllegalStateException("Invalid config: streaming.ffmpeg-path must be set");
        }
        if (this.streaming.idleTimeout().isNegative() || this.streaming.idleTimeout().isZero()) {
            throw new IllegalStateException("Invalid config: streaming.idle-timeout-seconds must be positive");
        }
        if (this.streaming.startupTimeout().isNegative() || this.streaming.startupTimeout().isZero()) {
            throw new IllegalStateException("Invalid config: streaming.startup-timeout-seconds must be positive");
        }
        if (this.transcoding.preset().isBlank()) {
            throw new IllegalStateException("Invalid config: transcoding.preset must be set");
        }
        Set<String> profileIds = new HashSet<>();
        for (TranscodeProfile profile : this.transcoding.profiles()) {
            if (!PROFILE_ID.matcher(profile.id()).matches()) {
                throw new IllegalStateException("Invalid config: transcoding profile IDs may only contain lowercase letters, digits, - and _, and auto is reserved");
            }
            if (!profileIds.add(profile.id())) {
                throw new IllegalStateException("Invalid config: there are several transcoding profiles with ID " + profile.id());
            }
            if (profile.height() < 144 || profile.height() > 4320 || profile.height() % 2 != 0) {
                throw new IllegalStateException("Invalid config: the height of transcoding profile " + profile.id() + " must be an even number between 144 and 4320");
            }
            if (profile.bitrateKbps() <= 0) {
                throw new IllegalStateException("Invalid config: the bitrate of transcoding profile " + profile.id() + " must be positive");
            }
        }
    }

    @NotNull
    public ServerConfig server() {
        return this.server;
    }

    @NotNull
    public DatabaseConfig database() {
        return this.database;
    }

    @NotNull
    public AuthConfig auth() {
        return this.auth;
    }

    @NotNull
    public StreamingConfig streaming() {
        return this.streaming;
    }

    @NotNull
    public TranscodingConfig transcoding() {
        return this.transcoding;
    }

    @ConfigSerializable
    public static class ServerConfig {

        /**
         * Used for the frontend's directory when the config doesn't set one, which is how the Docker image sets it
         */
        private static final String FRONTEND_DIR_ENV = "KIJKBUIS_FRONTEND_DIR";

        private String host = "0.0.0.0";
        private int port = 6742;
        private String publicUrl = "http://localhost:6742";
        private String frontendDir = "";

        @NotNull
        public String host() {
            return this.host;
        }

        public int port() {
            return this.port;
        }

        /**
         * @return the URL users reach Kijkbuis on, without a trailing slash
         */
        @NotNull
        public String publicUrl() {
            return this.publicUrl.endsWith("/") ? this.publicUrl.substring(0, this.publicUrl.length() - 1) : this.publicUrl;
        }

        /**
         * @return the directory with the built frontend to serve, or null to only serve the API (e.g. when Vite's dev
         * server serves the frontend)
         */
        @Nullable
        public Path frontendDir() {
            String dir = !this.frontendDir.isBlank() ? this.frontendDir : System.getenv(FRONTEND_DIR_ENV);
            return dir != null && !dir.isBlank() ? Path.of(dir) : null;
        }

        /**
         * @return whether cookies should be marked Secure, which is only possible when Kijkbuis is served over HTTPS
         */
        public boolean secureCookies() {
            return this.publicUrl.startsWith("https://");
        }

    }

    @ConfigSerializable
    public static class DatabaseConfig {

        private String path = "kijkbuis.db";

        @NotNull
        public String path() {
            return this.path;
        }

    }

    @ConfigSerializable
    public static class AuthConfig {

        /**
         * Grants a privilege to every user who can log in, when used instead of a group name.
         */
        public static final String EVERYONE = "*";

        private String issuer = "";
        private String clientId = "";
        private String clientSecret = "";
        private List<String> scopes = List.of("openid", "profile", "email");
        private String groupsClaim = "groups";
        private int sessionLifetimeHours = 720;
        private Map<Privilege, List<String>> privileges = Map.of();

        @NotNull
        public String issuer() {
            return this.issuer;
        }

        @NotNull
        public String clientId() {
            return this.clientId;
        }

        /**
         * @return the client secret, or an empty string for public clients
         */
        @NotNull
        public String clientSecret() {
            return this.clientSecret;
        }

        @NotNull
        public List<String> scopes() {
            return this.scopes;
        }

        @NotNull
        public String groupsClaim() {
            return this.groupsClaim;
        }

        @NotNull
        public Duration sessionLifetime() {
            return Duration.ofHours(this.sessionLifetimeHours);
        }

        /**
         * Determines which privileges members of the given groups have.
         * Privileges given to {@link #EVERYONE} are granted whatever the groups, and admins are granted every privilege.
         *
         * @param groups the groups the user is a member of
         * @return the privileges granted to the user
         */
        @NotNull
        public Set<Privilege> privilegesFor(@NotNull Collection<String> groups) {
            Set<Privilege> granted = EnumSet.noneOf(Privilege.class);
            this.privileges.forEach((privilege, privilegeGroups) -> {
                if (privilegeGroups.contains(EVERYONE) || privilegeGroups.stream().anyMatch(groups::contains)) {
                    granted.add(privilege);
                }
            });

            if (granted.contains(Privilege.ADMIN)) {
                return EnumSet.allOf(Privilege.class);
            }
            return granted;
        }

    }

    @ConfigSerializable
    public static class StreamingConfig {

        private String ffmpegPath = "ffmpeg";
        private int idleTimeoutSeconds = 30;
        private int startupTimeoutSeconds = 20;

        @NotNull
        public String ffmpegPath() {
            return this.ffmpegPath;
        }

        /**
         * @return how long a stream may go without requests before it's stopped
         */
        @NotNull
        public Duration idleTimeout() {
            return Duration.ofSeconds(this.idleTimeoutSeconds);
        }

        /**
         * @return how long to wait for a stream's first segment
         */
        @NotNull
        public Duration startupTimeout() {
            return Duration.ofSeconds(this.startupTimeoutSeconds);
        }

    }

    @ConfigSerializable
    public static class TranscodingConfig {

        private String preset = "veryfast";
        private boolean deinterlaceDoubleRate = true;
        private List<TranscodeProfile> profiles = List.of();

        /**
         * @return the libx264 preset
         */
        @NotNull
        public String preset() {
            return this.preset;
        }

        /**
         * @return whether deinterlacing turns every field into a frame (50i into 50p), rather than every frame (25p)
         */
        public boolean deinterlaceDoubleRate() {
            return this.deinterlaceDoubleRate;
        }

        @NotNull
        public List<TranscodeProfile> profiles() {
            return this.profiles;
        }

        @Nullable
        public TranscodeProfile profile(@NotNull String id) {
            return this.profiles.stream().filter((profile) -> profile.id().equals(id)).findFirst().orElse(null);
        }

    }

    /**
     * A quality users can pick.
     */
    @ConfigSerializable
    public static class TranscodeProfile implements KBSerializable {

        private String id = "";
        private String name = "";
        private int height = 0;
        private int bitrateKbps = 0;

        public TranscodeProfile() {
        }

        public TranscodeProfile(@NotNull String id, @NotNull String name, int height, int bitrateKbps) {
            this.id = id;
            this.name = name;
            this.height = height;
            this.bitrateKbps = bitrateKbps;
        }

        @NotNull
        public String id() {
            return this.id;
        }

        @NotNull
        public String name() {
            return this.name.isBlank() ? this.id : this.name;
        }

        /**
         * @return the height of the profile's box, see {@link #width()}
         */
        public int height() {
            return this.height;
        }

        /**
         * @return the width of a 16:9 picture of the profile's height, rounded to an even number. Channels are scaled
         * down to fit in this box, whatever their aspect ratio.
         */
        public int width() {
            return (int) Math.round(this.height * 16 / 9.0 / 2) * 2;
        }

        public int bitrateKbps() {
            return this.bitrateKbps;
        }

        @Override
        @NotNull
        public JsonObject serialize(@NotNull User user) {
            JsonObject obj = new JsonObject();

            obj.addProperty("id", this.id);
            obj.addProperty("name", this.name());
            obj.addProperty("width", this.width());
            obj.addProperty("height", this.height);
            obj.addProperty("bitrateKbps", this.bitrateKbps);

            return obj;
        }

    }

}
