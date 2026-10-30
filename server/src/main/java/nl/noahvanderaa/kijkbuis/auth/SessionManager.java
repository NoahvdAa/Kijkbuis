package nl.noahvanderaa.kijkbuis.auth;

import nl.noahvanderaa.kijkbuis.database.Database;
import nl.noahvanderaa.kijkbuis.user.User;
import nl.noahvanderaa.kijkbuis.user.UserManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps track of login sessions. Only a hash of each session token is stored, so a copy of the database (e.g. a
 * backup) can't be used to log in.
 */
public class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Database database;
    private final UserManager userManager;
    private final Duration lifetime;

    public SessionManager(@NotNull Database database, @NotNull UserManager userManager, @NotNull Duration lifetime) {
        this.database = database;
        this.userManager = userManager;
        this.lifetime = lifetime;
    }

    /**
     * Starts a new session for the given user.
     *
     * @param user the user that logged in
     * @return the session token to hand to the client
     */
    @NotNull
    public String create(@NotNull User user) throws SQLException {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        long now = System.currentTimeMillis();
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, hash(token));
            statement.setString(2, user.id().toString());
            statement.setLong(3, now);
            statement.setLong(4, now + this.lifetime.toMillis());
            statement.executeUpdate();
        }

        return token;
    }

    /**
     * Looks up the user a session token belongs to.
     *
     * @param token the session token sent by the client, if any
     * @return the user, or null if the token is missing, unknown or expired
     */
    @Nullable
    public User resolve(@Nullable String token) throws SQLException {
        if (token == null || token.isEmpty()) {
            return null;
        }

        // One query, since this runs for nearly every request. There's a row per group, or one row without groups.
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT u.id, u.username, u.display_name, g.group_name FROM sessions s JOIN users u ON u.id = s.user_id " +
                             "LEFT JOIN user_groups g ON g.user_id = u.id WHERE s.token_hash = ? AND s.expires_at > ?"
             )) {
            statement.setString(1, hash(token));
            statement.setLong(2, System.currentTimeMillis());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                UUID id = UUID.fromString(resultSet.getString("id"));
                String username = resultSet.getString("username");
                String displayName = resultSet.getString("display_name");
                Set<String> groups = new HashSet<>();
                do {
                    if (resultSet.getString("group_name") != null) {
                        groups.add(resultSet.getString("group_name"));
                    }
                } while (resultSet.next());

                return this.userManager.user(id, username, displayName, groups);
            }
        }
    }

    public void delete(@NotNull String token) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE token_hash = ?")) {
            statement.setString(1, hash(token));
            statement.executeUpdate();
        }
    }

    /**
     * Signs a user out everywhere.
     *
     * @return the number of sessions that were removed
     */
    public int deleteAllOf(@NotNull UUID userId) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE user_id = ?")) {
            statement.setString(1, userId.toString());
            return statement.executeUpdate();
        }
    }

    /**
     * @return the number of expired sessions that were removed
     */
    public int deleteExpired() throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
            statement.setLong(1, System.currentTimeMillis());
            return statement.executeUpdate();
        }
    }

    @NotNull
    public Duration lifetime() {
        return this.lifetime;
    }

    /**
     * @return what identifies a session without being its token: the hash of the token, as the database stores it
     */
    @NotNull
    public static String idOf(@NotNull String token) {
        return hash(token);
    }

    @NotNull
    private static String hash(@NotNull String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

}
