package nl.noahvanderaa.kijkbuis.database;

import me.noahvdaa.schemashift.migration.Migration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A migration that runs every statement in an SQL script.
 * Schemashift's own migrations run their script with {@link Statement#execute(String)}, which the SQLite driver only
 * executes the first statement of. {@link Statement#executeUpdate(String)} runs all of them.
 *
 * @param id     the migration ID
 * @param script the SQL script
 */
public record ScriptMigration(@NotNull String id, @NotNull String script) implements Migration {

    @NotNull
    public static ScriptMigration fromResource(@NotNull String path) throws IOException {
        try (InputStream in = ScriptMigration.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new FileNotFoundException("Migration " + path + " not found");
            }
            // Same ID as Migration.fromResource gives it, so switching back to that doesn't re-run any migrations
            return new ScriptMigration(new File(path).getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(this.script);
        }
    }

}
