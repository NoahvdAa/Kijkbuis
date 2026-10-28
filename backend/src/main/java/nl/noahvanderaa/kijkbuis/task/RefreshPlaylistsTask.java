package nl.noahvanderaa.kijkbuis.task;

import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class RefreshPlaylistsTask implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger(RefreshPlaylistsTask.class);
    private final KijkbuisApp app;

    public RefreshPlaylistsTask(@NotNull KijkbuisApp app) {
        this.app = app;
    }

    @Override
    public void run() {
        // Catch everything, an exception would cancel all future runs of this task
        try {
            this.app.playlistManager().refreshDue();
        } catch (Exception ex) {
            LOGGER.warn("Failed to start playlist refreshes", ex);
        }
    }

}
