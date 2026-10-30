package nl.noahvanderaa.kijkbuis.api.ui;

import nl.noahvanderaa.kijkbuis.api.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Saves the forms sent to an action of the plugin, see {@link UiManager#handleForm}. It's called on the thread of the
 * request, so it must be thread-safe.
 */
@FunctionalInterface
public interface FormHandler {

    /**
     * Saves what was filled in. Returning means it's saved, and the viewer sees the form's success text.
     * <p>
     * Anyone who's logged in can send anything here, not only what the form allows. So check the values, and whether
     * the viewer may save them, such as only admins changing the plugin's settings.
     *
     * @param viewer who sent the form
     * @param values what they filled in
     * @throws FormException to tell the viewer why it can't be saved
     * @throws Exception     if something else went wrong, which is logged. The viewer only hears that it failed.
     */
    void handle(@NotNull User viewer, @NotNull FormValues values) throws Exception;

}
