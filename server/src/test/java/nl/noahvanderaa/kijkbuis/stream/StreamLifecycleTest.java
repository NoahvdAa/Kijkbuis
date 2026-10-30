package nl.noahvanderaa.kijkbuis.stream;

import nl.noahvanderaa.kijkbuis.channel.Channel;
import nl.noahvanderaa.kijkbuis.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugins hear that a stream started before they hear it stopped, even when it's stopped while it starts.
 */
class StreamLifecycleTest {

    private final List<String> announced = new CopyOnWriteArrayList<>();

    @Test
    void streamsThatStartedAnnounceStopping() {
        Stream stream = stream();
        assertFalse(stream.isRunning(), "Not before it started");
        assertTrue(stream.markStarted(() -> this.announced.add("started, running " + stream.isRunning())));
        assertTrue(stream.isRunning());
        stream.stop();
        stream.announceStopped(() -> this.announced.add("stopped, running " + stream.isRunning()));
        assertEquals(List.of("started, running true", "stopped, running false"), this.announced);
    }

    @Test
    void streamsStoppedBeforeTheyStartedAnnounceNothing() {
        Stream stream = stream();
        stream.stop();
        assertFalse(stream.markStarted(() -> this.announced.add("started")));
        stream.announceStopped(() -> this.announced.add("stopped"));
        assertEquals(List.of(), this.announced);
        assertFalse(stream.isRunning());
    }

    @Test
    void stoppingWaitsForTheAnnouncementThatItStarted() throws Exception {
        Stream stream = stream();
        CountDownLatch announcing = new CountDownLatch(1);
        Thread starting = Thread.ofVirtual().start(() -> stream.markStarted(() -> {
            announcing.countDown();
            try {
                // Plugins taking their time to hear it started
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            this.announced.add("started");
        }));

        assertTrue(announcing.await(5, TimeUnit.SECONDS));
        stream.stop();
        stream.announceStopped(() -> this.announced.add("stopped"));
        starting.join();
        assertEquals(List.of("started", "stopped"), this.announced);
    }

    private static Stream stream() {
        User user = new User(UUID.randomUUID(), "viewer", "Viewer", Set.of(), Set.of());
        Channel channel = new Channel(UUID.randomUUID(), UUID.randomUUID(), "Live", null, null, "http://upstream.example/live.ts", null, null, null);
        return new Stream(user, "session", channel, StreamManager.AUTO_QUALITY, new EncodingPlan(false, null, false, false));
    }

}
