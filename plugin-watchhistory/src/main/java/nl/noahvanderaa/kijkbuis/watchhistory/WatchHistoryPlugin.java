package nl.noahvanderaa.kijkbuis.watchhistory;

import nl.noahvanderaa.kijkbuis.api.plugin.KijkbuisPlugin;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginContext;
import nl.noahvanderaa.kijkbuis.watchhistory.page.OverviewPage;
import nl.noahvanderaa.kijkbuis.watchhistory.session.SessionManager;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class WatchHistoryPlugin implements KijkbuisPlugin {

    private final SessionManager sessionManager = new SessionManager(this);
    private PluginContext context;

    @Override
    public void onEnable(@NotNull PluginContext context) {
        this.context = context;
        context.events().register(new StreamListener(this));
        context.scheduler().runRepeating(Duration.ZERO, Duration.ofMinutes(5), this.sessionManager::autoSave);

        context.ui().registerPage("watch-history", "Watch History", new OverviewPage(this));
    }

    @NotNull
    public SessionManager sessionManager() {
        return this.sessionManager;
    }

    @NotNull
    public PluginContext context() {
        return this.context;
    }

}
