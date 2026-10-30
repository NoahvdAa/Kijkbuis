package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.event.Cancellable;
import nl.noahvanderaa.kijkbuis.api.event.Event;
import nl.noahvanderaa.kijkbuis.api.event.EventHandler;
import nl.noahvanderaa.kijkbuis.api.event.EventPriority;
import nl.noahvanderaa.kijkbuis.api.event.Listener;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Calls the event handlers of plugins, on the thread that calls the event. Handlers run from the lowest priority to
 * the highest, and in the order they were registered within a priority.
 */
public class EventBus {

    private static final Logger LOGGER = LogManager.getLogger(EventBus.class);

    private final Object lock = new Object();
    /**
     * Every handler in the order they run. Changes replace the whole list, so calling events doesn't need the lock.
     */
    private volatile List<RegisteredHandler> handlers = List.of();

    /**
     * Registers every event handler in a listener.
     *
     * @param plugin the plugin the listener belongs to
     * @throws IllegalArgumentException if one of them doesn't take a single event
     */
    public void register(@NotNull PluginInfo plugin, @NotNull Listener listener) {
        List<RegisteredHandler> added = new ArrayList<>();
        for (Method method : handlerMethods(listener.getClass())) {
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException(listener.getClass().getName() + "." + method.getName() + " is an event handler, so it must take a single event");
            }
            // Listeners don't have to be public classes
            method.setAccessible(true);
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            added.add(new RegisteredHandler(plugin, listener, method, method.getParameterTypes()[0], annotation.priority(), annotation.ignoreCancelled()));
        }

        synchronized (this.lock) {
            List<RegisteredHandler> handlers = new ArrayList<>(this.handlers);
            handlers.addAll(added);
            // Sorting is stable, so handlers with the same priority stay in the order they were registered in
            handlers.sort(Comparator.comparing(RegisteredHandler::priority));
            this.handlers = List.copyOf(handlers);
        }
    }

    public void unregister(@NotNull Listener listener) {
        this.unregisterIf((handler) -> handler.listener() == listener);
    }

    /**
     * Unregisters every listener of a plugin, such as when it's disabled.
     */
    public void unregisterAll(@NotNull PluginInfo plugin) {
        this.unregisterIf((handler) -> handler.plugin().id().equals(plugin.id()));
    }

    /**
     * Calls every handler for the event. A handler that throws is logged, and the others still run.
     *
     * @return the event, to see whether it was cancelled
     */
    @NotNull
    public <T extends Event> T call(@NotNull T event) {
        for (RegisteredHandler handler : this.handlers) {
            if (!handler.eventType().isInstance(event)) {
                continue;
            }
            if (handler.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                continue;
            }

            try {
                handler.method().invoke(handler.listener(), event);
            } catch (InvocationTargetException ex) {
                LOGGER.error("{} failed to handle {}", handler.plugin().name(), event.getClass().getSimpleName(), ex.getCause());
            } catch (IllegalAccessException ex) {
                // The method was made accessible when it was registered
                throw new IllegalStateException(ex);
            }
        }
        return event;
    }

    private void unregisterIf(@NotNull Predicate<RegisteredHandler> filter) {
        synchronized (this.lock) {
            this.handlers = this.handlers.stream().filter(filter.negate()).toList();
        }
    }

    /**
     * @return the methods annotated with {@link EventHandler}, including inherited ones
     */
    @NotNull
    private static List<Method> handlerMethods(@NotNull Class<?> type) {
        // Public methods include inherited ones, declared methods include private ones
        Set<Method> methods = new LinkedHashSet<>(List.of(type.getMethods()));
        methods.addAll(List.of(type.getDeclaredMethods()));
        return methods.stream()
                .filter((method) -> method.isAnnotationPresent(EventHandler.class) && !method.isBridge() && !method.isSynthetic())
                .toList();
    }

    private record RegisteredHandler(
            @NotNull PluginInfo plugin,
            @NotNull Listener listener,
            @NotNull Method method,
            @NotNull Class<?> eventType,
            @NotNull EventPriority priority,
            boolean ignoreCancelled
    ) {
    }

}
