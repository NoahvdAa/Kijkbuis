package nl.noahvanderaa.kijkbuis.api.scheduler;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Runs a plugin's tasks later, or every so often.
 * <p>
 * Every run gets a thread of its own, so tasks can take their time without holding up anything else, and must be
 * thread-safe. A repeating task never runs twice at once: if a run takes longer than the period, the next one starts
 * right after it.
 * <p>
 * When the plugin is disabled, its tasks are cancelled, and the ones that are running get a few seconds to finish
 * before {@link nl.noahvanderaa.kijkbuis.api.plugin.KijkbuisPlugin#onDisable()} is called. Tasks that are waiting to
 * run don't run anymore.
 */
public interface Scheduler {

    /**
     * Runs a task right away, on a thread of its own, without waiting for it. That's for work that shouldn't hold up
     * whatever called the plugin, such as writing to the database in an event handler.
     * <p>
     * The task is running by the time this returns, so it gets to finish when the plugin is disabled, like other
     * running tasks.
     *
     * @throws IllegalStateException if the plugin is disabled
     */
    @NotNull
    Task run(@NotNull Runnable task);

    /**
     * Runs a task once.
     *
     * @param delay how long to wait first, which can be zero to run it right away
     * @throws IllegalArgumentException if the delay is negative
     * @throws IllegalStateException    if the plugin is disabled
     */
    @NotNull
    Task runLater(@NotNull Duration delay, @NotNull Runnable task);

    /**
     * Runs a task once. It gets its own {@link Task}, so it can see whether it's cancelled while it's running.
     *
     * @param delay how long to wait first, which can be zero to run it right away
     * @throws IllegalArgumentException if the delay is negative
     * @throws IllegalStateException    if the plugin is disabled
     */
    @NotNull
    Task runLater(@NotNull Duration delay, @NotNull Consumer<Task> task);

    /**
     * Runs a task every so often, until it's cancelled.
     *
     * @param delay  how long to wait before the first run, which can be zero to start right away
     * @param period how long from the start of one run to the start of the next
     * @throws IllegalArgumentException if the delay is negative, or the period isn't positive
     * @throws IllegalStateException    if the plugin is disabled
     */
    @NotNull
    Task runRepeating(@NotNull Duration delay, @NotNull Duration period, @NotNull Runnable task);

    /**
     * Runs a task every so often, until it's cancelled. It gets its own {@link Task}, so it can cancel itself, such as
     * once its work is done.
     *
     * @param delay  how long to wait before the first run, which can be zero to start right away
     * @param period how long from the start of one run to the start of the next
     * @throws IllegalArgumentException if the delay is negative, or the period isn't positive
     * @throws IllegalStateException    if the plugin is disabled
     */
    @NotNull
    Task runRepeating(@NotNull Duration delay, @NotNull Duration period, @NotNull Consumer<Task> task);

}
