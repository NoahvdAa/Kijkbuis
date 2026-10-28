package nl.noahvanderaa.kijkbuis.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.noahvdaa.schemashift.SchemaShift;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public class Database {

    private static final Logger LOGGER = LogManager.getLogger(Database.class);
    private HikariDataSource dataSource = null;

    public void initialize(@NotNull Path path) throws SQLException, IOException {
        if (this.dataSource != null) {
            throw new IllegalStateException("The database is already initialized!");
        }
        LOGGER.info("Initializing database...");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + path);
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        // With WAL, NORMAL is still safe if Kijkbuis crashes, only a power cut can lose the latest commits. It syncs
        // to disk far less often than the default (FULL), which saves disk writes.
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        // Transactions take the write lock right away. Otherwise one that reads first and writes later fails at once
        // when another connection wrote in between (e.g. a playlist refresh during an EPG import), instead of waiting.
        config.addDataSourceProperty("transaction_mode", "IMMEDIATE");
        this.dataSource = new HikariDataSource(config);

        LOGGER.info("Running migrations...");
        try (Connection connection = this.dataSource.getConnection()) {
            SchemaShift migrator = SchemaShift.with(connection);

            migrator.registerMigrations(
                    ScriptMigration.fromResource("migrations/2026-10-26_12-00_Init.sql")
            );

            if (migrator.migrateLatest()) {
                LOGGER.info("Migrations applied successfully!");
            } else {
                LOGGER.info("No migrations to apply!");
            }
        }

        LOGGER.info("Database ready!");
    }

    @NotNull
    public Connection connection() throws SQLException {
        if (this.dataSource == null) {
            throw new IllegalStateException("The database is not initialized!");
        }
        return this.dataSource.getConnection();
    }

    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

}
