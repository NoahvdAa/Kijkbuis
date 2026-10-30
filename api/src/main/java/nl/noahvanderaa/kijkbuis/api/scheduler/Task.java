package nl.noahvanderaa.kijkbuis.api.scheduler;

/**
 * A task the {@link Scheduler} runs.
 */
public interface Task {

    /**
     * Stops the task from running (again). A run that's busy finishes first. Cancelling a task more than once does
     * nothing.
     */
    void cancel();

    /**
     * @return whether the task is cancelled: by itself, by the plugin, or by Kijkbuis when the plugin was disabled
     */
    boolean isCancelled();

}
