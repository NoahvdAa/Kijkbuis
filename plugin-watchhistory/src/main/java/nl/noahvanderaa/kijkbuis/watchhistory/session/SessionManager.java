package nl.noahvanderaa.kijkbuis.watchhistory.session;

import nl.noahvanderaa.kijkbuis.api.stream.Stream;
import nl.noahvanderaa.kijkbuis.watchhistory.WatchHistoryPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final WatchHistoryPlugin plugin;
    private final Map<UUID, StreamSession> sessions = new ConcurrentHashMap<>();

    public SessionManager(@NotNull WatchHistoryPlugin plugin) {
        this.plugin = plugin;
    }

    public void startSession(@NotNull Stream stream) {
        this.sessions.put(stream.id(), new StreamSession(this.plugin, stream));
    }

    public void stopSession(@NotNull Stream stream) {
        StreamSession session = this.sessions.remove(stream.id());
        if (session != null) {
            session.cleanup();
        }
    }

    public void autoSave() {
        for (StreamSession session : this.sessions.values()) {
            session.save();
        }
    }

}
