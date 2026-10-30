package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.scheduler.Scheduler;
import nl.noahvanderaa.kijkbuis.api.scheduler.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the tasks of one plugin. A timer that every plugin shares decides when tasks are due, and each run gets a
 * virtual thread of its own. So a task that takes long doesn't hold up the others, of this plugin or any other.
 */
final class PluginScheduler implements Scheduler {

    private static final Logger LOGGER = LogManager.getLogger(PluginScheduler.class);

    private final PluginInfo plugin;
    private final ScheduledExecutorService timer;
    /**
     * The tasks that will run, or are running
     */
    private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();
    /**
     * Makes sure no task is added while the scheduler closes, which would then miss it
     */
    private final Object lock = new Object();
    private boolean closed = false;

    /**
     * @param timer what decides when tasks are due. Its thread only starts runs, so it's never kept busy.
     */
    PluginScheduler(@NotNull PluginInfo plugin, @NotNull ScheduledExecutorService timer) {
        this.plugin = plugin;
        this.timer = timer;
    }

    @Override
    @NotNull
    public Task run(@NotNull Runnable task) {
        return this.schedule(Duration.ZERO, null, (self) -> task.run());
    }

    @Override
    @NotNull
    public Task runLater(@NotNull Duration delay, @NotNull Runnable task) {
        return this.schedule(delay, null, (self) -> task.run());
    }

    @Override
    @NotNull
    public Task runLater(@NotNull Duration delay, @NotNull Consumer<Task> task) {
        return this.schedule(delay, null, task);
    }

    @Override
    @NotNull
    public Task runRepeating(@NotNull Duration delay, @NotNull Duration period, @NotNull Runnable task) {
        return this.schedule(delay, period, (self) -> task.run());
    }

    @Override
    @NotNull
    public Task runRepeating(@NotNull Duration delay, @NotNull Duration period, @NotNull Consumer<Task> task) {
        return this.schedule(delay, period, task);
    }

    /**
     * Cancels every task, and waits for the ones that are running to finish, such as when the plugin is disabled.
     * Tasks can't be scheduled anymore afterwards.
     *
     * @param timeout how long to wait at most
     */
    void close(@NotNull Duration timeout) {
        List<ScheduledTask> tasks;
        synchronized (this.lock) {
            this.closed = true;
            tasks = List.copyOf(this.tasks);
        }
        tasks.forEach(ScheduledTask::cancel);

        long deadline = System.nanoTime() + timeout.toNanos();
        for (ScheduledTask task : tasks) {
            if (!task.awaitRun(deadline)) {
                LOGGER.warn("A task of {} is still running, Kijkbuis isn't waiting for it any longer", this.plugin.name());
                return;
            }
        }
    }

    @NotNull
    private Task schedule(@NotNull Duration delay, @Nullable Duration period, @NotNull Consumer<Task> body) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("The delay can't be negative");
        }
        if (period != null && (period.isZero() || period.isNegative())) {
            throw new IllegalArgumentException("The period must be positive");
        }
        ScheduledTask task = new ScheduledTask(body, period);
        synchronized (this.lock) {
            if (this.closed) {
                throw new IllegalStateException(this.plugin.name() + " is disabled");
            }
            this.tasks.add(task);
        }
        task.scheduleRun(System.nanoTime() + delay.toNanos());
        return task;
    }

    private final class ScheduledTask implements Task {

        private final Consumer<Task> body;
        private final Duration period;
        private volatile boolean cancelled = false;
        /**
         * When the next run is due, as a {@link System#nanoTime()}
         */
        private long nextRunAt;
        private ScheduledFuture<?> nextRun = null;
        private Thread runningOn = null;

        /**
         * @param period the time between the starts of runs, or null to run once
         */
        ScheduledTask(@NotNull Consumer<Task> body, @Nullable Duration period) {
            this.body = body;
            this.period = period;
        }

        @Override
        public synchronized void cancel() {
            this.cancelled = true;
            if (this.nextRun != null) {
                this.nextRun.cancel(false);
            }
            // A run that's busy forgets the task itself once it's done
            if (this.runningOn == null) {
                PluginScheduler.this.tasks.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        /**
         * @param at when the run is due, as a {@link System#nanoTime()}
         */
        private synchronized void scheduleRun(long at) {
            if (this.cancelled) {
                return;
            }
            this.nextRunAt = at;
            long delay = at - System.nanoTime();
            if (delay <= 0) {
                // Without going through the timer, so it's running before whoever scheduled it moves on. That way it
                // isn't dropped when the plugin is disabled right after.
                this.start();
            } else {
                this.nextRun = PluginScheduler.this.timer.schedule(this::start, delay, TimeUnit.NANOSECONDS);
            }
        }

        /**
         * Starts a run, on the timer's thread.
         */
        private synchronized void start() {
            if (this.cancelled) {
                return;
            }
            this.nextRun = null;
            this.runningOn = Thread.ofVirtual().name("plugin-" + PluginScheduler.this.plugin.id() + "-task").start(this::run);
        }

        private void run() {
            try {
                this.body.accept(this);
            } catch (Exception | LinkageError ex) {
                LOGGER.error("A task of {} failed", PluginScheduler.this.plugin.name(), ex);
            } finally {
                this.finishRun();
            }
        }

        private synchronized void finishRun() {
            this.runningOn = null;
            if (this.period == null || this.cancelled) {
                PluginScheduler.this.tasks.remove(this);
                return;
            }
            // Keeps to the period, without catching up on runs that were missed because this one took too long
            this.scheduleRun(Math.max(this.nextRunAt + this.period.toNanos(), System.nanoTime()));
        }

        /**
         * Waits until the run that's busy, if any, is done.
         *
         * @param deadline when to stop waiting, as a {@link System#nanoTime()}
         * @return whether no run is busy anymore
         */
        private boolean awaitRun(long deadline) {
            Thread thread;
            synchronized (this) {
                thread = this.runningOn;
            }
            if (thread == null) {
                return true;
            }
            try {
                return thread.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

    }

}
