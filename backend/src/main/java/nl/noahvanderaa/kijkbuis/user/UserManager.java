package nl.noahvanderaa.kijkbuis.user;

import nl.noahvanderaa.kijkbuis.auth.OidcIdentity;
import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import nl.noahvanderaa.kijkbuis.database.Database;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserManager {

    private final Database database;
    private final KijkbuisConfig.AuthConfig authConfig;

    public UserManager(@NotNull Database database, @NotNull KijkbuisConfig.AuthConfig authConfig) {
        this.database = database;
        this.authConfig = authConfig;
    }

    /**
     * Creates or updates the user belonging to an SSO identity.
     * The user's profile and groups are replaced with the ones reported by the identity provider.
     *
     * @param identity the identity reported by the identity provider
     * @return the up-to-date user
     */
    @NotNull
    public User upsert(@NotNull OidcIdentity identity) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try {
                UUID id;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users (id, oidc_issuer, oidc_subject, username, display_name, created_at, last_login_at) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                                "ON CONFLICT (oidc_issuer, oidc_subject) DO UPDATE SET username = excluded.username, display_name = excluded.display_name, last_login_at = excluded.last_login_at " +
                                "RETURNING id"
                )) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, identity.issuer());
                    statement.setString(3, identity.subject());
                    statement.setString(4, identity.username());
                    statement.setString(5, identity.displayName());
                    statement.setLong(6, now);
                    statement.setLong(7, now);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        id = UUID.fromString(resultSet.getString("id"));
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_groups WHERE user_id = ?")) {
                    statement.setString(1, id.toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO user_groups (user_id, group_name) VALUES (?, ?)")) {
                    for (String group : identity.groups()) {
                        statement.setString(1, id.toString());
                        statement.setString(2, group);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                connection.commit();
                return this.user(id, identity.username(), identity.displayName(), identity.groups());
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    /**
     * @return every user that ever logged in, the most recent login first
     */
    @NotNull
    public List<UserDetails> list() throws SQLException {
        try (Connection connection = this.database.connection()) {
            Map<UUID, Set<String>> groups = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT user_id, group_name FROM user_groups");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    groups.computeIfAbsent(UUID.fromString(resultSet.getString("user_id")), (id) -> new HashSet<>()).add(resultSet.getString("group_name"));
                }
            }

            List<UserDetails> users = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT u.id, u.username, u.display_name, u.created_at, u.last_login_at, " +
                            "(SELECT COUNT(*) FROM sessions s WHERE s.user_id = u.id AND s.expires_at > ?) AS session_count " +
                            "FROM users u ORDER BY u.last_login_at DESC"
            )) {
                statement.setLong(1, System.currentTimeMillis());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        UUID id = UUID.fromString(resultSet.getString("id"));
                        User user = this.user(id, resultSet.getString("username"), resultSet.getString("display_name"), groups.getOrDefault(id, Set.of()));
                        users.add(new UserDetails(user, Instant.ofEpochMilli(resultSet.getLong("created_at")),
                                Instant.ofEpochMilli(resultSet.getLong("last_login_at")), resultSet.getInt("session_count")));
                    }
                }
            }
            return users;
        }
    }

    /**
     * Creates a user object, granting the privileges their groups give them.
     */
    @NotNull
    public User user(@NotNull UUID id, @NotNull String username, @NotNull String displayName, @NotNull Collection<String> groups) {
        return new User(id, username, displayName, Set.copyOf(groups), this.authConfig.privilegesFor(groups));
    }

}
