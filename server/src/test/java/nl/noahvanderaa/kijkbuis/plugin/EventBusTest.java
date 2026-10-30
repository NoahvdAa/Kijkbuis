package nl.noahvanderaa.kijkbuis.plugin;

import nl.noahvanderaa.kijkbuis.api.event.Cancellable;
import nl.noahvanderaa.kijkbuis.api.event.Event;
import nl.noahvanderaa.kijkbuis.api.event.EventHandler;
import nl.noahvanderaa.kijkbuis.api.event.EventPriority;
import nl.noahvanderaa.kijkbuis.api.event.Listener;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventBusTest {

    private static final PluginInfo PLUGIN = new PluginInfo("plugin", "Plugin", "1.0.0", "Plugin", "0.0", null, null);
    private static final PluginInfo OTHER_PLUGIN = new PluginInfo("other", "Other plugin", "1.0.0", "Other", "0.0", null, null);

    private final EventBus bus = new EventBus();
    private final List<String> calls = new ArrayList<>();

    @Test
    void runsHandlersFromLowestPriorityToMonitor() {
        this.bus.register(PLUGIN, new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void monitor(TestEvent event) {
                calls.add("monitor");
            }

            @EventHandler(priority = EventPriority.HIGHEST)
            public void highest(TestEvent event) {
                calls.add("highest");
            }

            @EventHandler
            public void normal(TestEvent event) {
                calls.add("normal");
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void lowest(TestEvent event) {
                calls.add("lowest");
            }
        });

        this.bus.call(new TestEvent());
        assertEquals(List.of("lowest", "normal", "highest", "monitor"), this.calls);
    }

    @Test
    void runsHandlersWithTheSamePriorityInTheOrderTheyWereRegistered() {
        this.bus.register(PLUGIN, this.recorder("first"));
        this.bus.register(OTHER_PLUGIN, new Listener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void high(TestEvent event) {
                calls.add("high");
            }
        });
        this.bus.register(OTHER_PLUGIN, this.recorder("second"));
        this.bus.register(PLUGIN, this.recorder("third"));

        this.bus.call(new TestEvent());
        assertEquals(List.of("first", "second", "third", "high"), this.calls);
    }

    @Test
    void handlersCanIgnoreCancelledEvents() {
        this.bus.register(PLUGIN, new Listener() {
            @EventHandler(priority = EventPriority.LOW)
            public void cancel(TestEvent event) {
                event.setCancelled(true);
            }

            @EventHandler(ignoreCancelled = true)
            public void ignoring(TestEvent event) {
                calls.add("ignoring");
            }

            @EventHandler(priority = EventPriority.HIGH)
            public void seeing(TestEvent event) {
                calls.add("seeing " + event.isCancelled());
            }

            @EventHandler(priority = EventPriority.HIGHEST)
            public void uncancel(TestEvent event) {
                event.setCancelled(false);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void monitor(TestEvent event) {
                calls.add("monitor");
            }
        });

        assertFalse(this.bus.call(new TestEvent()).isCancelled());
        assertEquals(List.of("seeing true", "monitor"), this.calls);
    }

    @Test
    void handlersGetSubclassesOfTheirEvent() {
        this.bus.register(PLUGIN, new Listener() {
            @EventHandler
            public void any(Event event) {
                calls.add("any " + event.getClass().getSimpleName());
            }
        });
        this.bus.register(PLUGIN, new Listener() {
            @EventHandler
            public void special(SpecialEvent event) {
                calls.add("special");
            }
        });

        this.bus.call(new TestEvent());
        this.bus.call(new SpecialEvent());
        assertEquals(List.of("any TestEvent", "any SpecialEvent", "special"), this.calls);
    }

    @Test
    void handlersThatThrowDoNotStopTheOthers() {
        this.bus.register(PLUGIN, new Listener() {
            @EventHandler(priority = EventPriority.LOW)
            public void broken(TestEvent event) {
                throw new IllegalStateException("Broken on purpose");
            }
        });
        this.bus.register(OTHER_PLUGIN, this.recorder("other"));

        this.bus.call(new TestEvent());
        assertEquals(List.of("other"), this.calls);
    }

    @Test
    void unregistersListeners() {
        Listener first = this.recorder("first");
        this.bus.register(PLUGIN, first);
        this.bus.register(PLUGIN, this.recorder("second"));
        this.bus.register(OTHER_PLUGIN, this.recorder("other"));

        this.bus.unregister(first);
        this.bus.call(new TestEvent());
        assertEquals(List.of("second", "other"), this.calls);

        this.calls.clear();
        this.bus.unregisterAll(PLUGIN);
        this.bus.call(new TestEvent());
        assertEquals(List.of("other"), this.calls);
    }

    @Test
    void rejectsHandlersThatDoNotTakeASingleEvent() {
        assertThrows(IllegalArgumentException.class, () -> this.bus.register(PLUGIN, new Listener() {
            @EventHandler
            public void notAnEvent(String text) {
            }
        }));
        assertThrows(IllegalArgumentException.class, () -> this.bus.register(PLUGIN, new Listener() {
            @EventHandler
            public void twoEvents(TestEvent first, TestEvent second) {
            }
        }));

        this.bus.call(new TestEvent());
        assertEquals(List.of(), this.calls, "Nothing of a rejected listener is registered");
    }

    /**
     * @return a listener that writes down its name when it gets a test event
     */
    private Listener recorder(String name) {
        return new Listener() {
            @EventHandler
            public void record(TestEvent event) {
                calls.add(name);
            }
        };
    }

    private static class TestEvent extends Event implements Cancellable {

        private boolean cancelled = false;

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

    }

    private static class SpecialEvent extends TestEvent {
    }

}
