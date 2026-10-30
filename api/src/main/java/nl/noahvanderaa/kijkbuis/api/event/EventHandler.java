package nl.noahvanderaa.kijkbuis.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in a {@link Listener} as an event handler. It must take a single {@link Event}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * @return when the handler runs compared to the others for the same event
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * @return whether to skip events another handler cancelled already, see {@link Cancellable}
     */
    boolean ignoreCancelled() default false;

}
