package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.scheduler.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSchedulerTest {

    private static final PluginInfo PLUGIN = new PluginInfo("plugin", "Plugin", "1.0.0", "Plugin", "0.0", null, null);
    private static final Duration SHORT = Duration.ofMillis(20);

    private final ScheduledThreadPoolExecutor timer = timer();
    private final PluginScheduler scheduler = new PluginScheduler(PLUGIN, this.timer);

    @AfterEach
    void stopTimer() {
        this.timer.shutdownNow();
    }

    @Test
    void runsTasksRightAwayOnAThreadOfTheirOwn() throws Exception {
        CompletableFuture<Thread> ranOn = new CompletableFuture<>();
        this.scheduler.run(() -> ranOn.complete(Thread.currentThread()));
        assertNotSame(Thread.currentThread(), ranOn.get(5, TimeUnit.SECONDS));
    }

    @Test
    void tasksThatRunRightAwayFinishWhenThePluginIsDisabled() {
        // Like an event handler handing off a database write just before Kijkbuis stops. Repeated, since getting this
        // wrong would be a race.
        for (int i = 0; i < 20; i++) {
            PluginScheduler scheduler = new PluginScheduler(PLUGIN, this.timer);
            AtomicBoolean finished = new AtomicBoolean(false);
            scheduler.run(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                finished.set(true);
            });
            scheduler.close(Duration.ofSeconds(5));
            assertTrue(finished.get(), "Attempt " + i);
        }
    }

    @Test
    void runsTasksOnceAfterTheirDelay() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        long scheduledAt = System.nanoTime();
        this.scheduler.runLater(Duration.ofMillis(200), () -> {
            runs.incrementAndGet();
            ran.countDown();
        });

        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertTrue(System.nanoTime() - scheduledAt >= Duration.ofMillis(200).toNanos(), "Not before the delay");
        Thread.sleep(200);
        assertEquals(1, runs.get());
    }

    @Test
    void repeatsTasksUntilTheyreCancelled() throws Exception {
        CountDownLatch ranThrice = new CountDownLatch(3);
        AtomicInteger runs = new AtomicInteger();
        Task task = this.scheduler.runRepeating(Duration.ZERO, SHORT, () -> {
            runs.incrementAndGet();
            ranThrice.countDown();
        });

        assertTrue(ranThrice.await(5, TimeUnit.SECONDS));
        task.cancel();
        assertTrue(task.isCancelled());
        // A run that was busy may still finish, but none start anymore
        Thread.sleep(SHORT.toMillis() * 2);
        int runsAfterCancel = runs.get();
        Thread.sleep(SHORT.toMillis() * 5);
        assertEquals(runsAfterCancel, runs.get());
    }

    @Test
    void tasksCanCancelThemselves() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        Task task = this.scheduler.runRepeating(Duration.ZERO, SHORT, (self) -> {
            if (runs.incrementAndGet() == 3) {
                self.cancel();
                done.countDown();
            }
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        Thread.sleep(SHORT.toMillis() * 5);
        assertEquals(3, runs.get());
        assertTrue(task.isCancelled());
    }

    @Test
    void repeatingTasksNeverRunTwiceAtOnce() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean(false);
        CountDownLatch ranThrice = new CountDownLatch(3);
        // Each run takes longer than the period
        Task task = this.scheduler.runRepeating(Duration.ZERO, SHORT, () -> {
            if (running.incrementAndGet() > 1) {
                overlapped.set(true);
            }
            try {
                Thread.sleep(SHORT.toMillis() * 3);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
            ranThrice.countDown();
        });

        assertTrue(ranThrice.await(5, TimeUnit.SECONDS));
        task.cancel();
        assertFalse(overlapped.get());
    }

    @Test
    void tasksKeepRepeatingWhenARunFails() throws Exception {
        CountDownLatch ranThrice = new CountDownLatch(3);
        this.scheduler.runRepeating(Duration.ZERO, SHORT, () -> {
            ranThrice.countDown();
            throw new IllegalStateException("Broken on purpose");
        });

        assertTrue(ranThrice.await(5, TimeUnit.SECONDS));
    }

    @Test
    void closingCancelsTasksAndWaitsForRunningOnes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean waitingRan = new AtomicBoolean(false);
        Task running = this.scheduler.runLater(Duration.ZERO, () -> {
            started.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
        });
        Task waiting = this.scheduler.runLater(Duration.ofHours(1), () -> waitingRan.set(true));

        assertTrue(started.await(5, TimeUnit.SECONDS));
        this.scheduler.close(Duration.ofSeconds(5));
        assertTrue(finished.get(), "The running task got to finish");
        assertTrue(running.isCancelled());
        assertTrue(waiting.isCancelled());
        assertFalse(waitingRan.get());
        assertEquals(0, this.timer.getQueue().size(), "Nothing is left on the timer");
        assertThrows(IllegalStateException.class, () -> this.scheduler.runLater(Duration.ZERO, () -> {
        }));
    }

    /**
     * @return a timer like the plugin manager's
     */
    private static ScheduledThreadPoolExecutor timer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    @Test
    void rejectsNegativeDelaysAndPeriods() {
        assertThrows(IllegalArgumentException.class, () -> this.scheduler.runLater(Duration.ofSeconds(-1), () -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> this.scheduler.runRepeating(Duration.ZERO, Duration.ZERO, () -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> this.scheduler.runRepeating(Duration.ZERO, Duration.ofSeconds(-1), () -> {
        }));
    }

}
