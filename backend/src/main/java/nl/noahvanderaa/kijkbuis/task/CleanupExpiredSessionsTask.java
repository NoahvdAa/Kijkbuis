package nl.noahvanderaa.kijkbuis.task;

import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class CleanupExpiredSessionsTask implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger(CleanupExpiredSessionsTask.class);
    private final KijkbuisApp app;

    public CleanupExpiredSessionsTask(@NotNull KijkbuisApp app) {
        this.app = app;
    }

    @Override
    public void run() {
        // Catch everything, an exception would cancel all future runs of this task
        try {
            int removed = this.app.sessionManager().deleteExpired();
            if (removed > 0) {
                LOGGER.debug("Removed {} expired sessions", removed);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to remove expired sessions", ex);
        }
    }

}
