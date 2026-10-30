package nl.noahvanderaa.kijkbuis.api.ui;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown by a {@link FormHandler} when what was filled in can't be saved. The viewer sees the message, so it should
 * say what to change, such as "Keep history for must be between 1 and 365 days".
 */
public class FormException extends RuntimeException {

    public FormException(@NotNull String message) {
        super(message);
    }

}
