package nl.noahvanderaa.kijkbuis.watchhistory;

import nl.noahvanderaa.kijkbuis.api.event.EventHandler;
import nl.noahvanderaa.kijkbuis.api.event.Listener;
import nl.noahvanderaa.kijkbuis.api.event.stream.StreamStartedEvent;
import nl.noahvanderaa.kijkbuis.api.event.stream.StreamStoppedEvent;
import org.jetbrains.annotations.NotNull;

public class StreamListener implements Listener {

	private final WatchHistoryPlugin plugin;

	public StreamListener(@NotNull WatchHistoryPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onStreamStarted(@NotNull StreamStartedEvent event) {
		this.plugin.sessionManager().startSession(event.stream());
	}

	@EventHandler
	public void onStreamStopped(@NotNull StreamStoppedEvent event) {
		this.plugin.sessionManager().stopSession(event.stream());
	}

}
