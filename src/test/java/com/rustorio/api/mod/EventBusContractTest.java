package com.rustorio.api.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Proves the {@link EventBus} contract itself, independent of {@code com.rustorio.mod}'s real
 * implementation (E7-01's own boundary: this card ships the interface, not the implementation) —
 * a minimal synthetic implementation here is enough to pin down what "subscribe"/"publish" must do.
 */
class EventBusContractTest {

    /** Minimal reference implementation — dispatches by the published event's own runtime class. */
    private static final class RecordingBus implements EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <E> void subscribe(Class<E> eventType, Consumer<E> handler) {
            handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add((Consumer<Object>) handler);
        }

        @Override
        public <E> void publish(E event) {
            List<Consumer<Object>> subscribed = handlers.get(event.getClass());
            if (subscribed != null) {
                subscribed.forEach(h -> h.accept(event));
            }
        }
    }

    @Test
    void subscribedHandlerReceivesAPublishedEventOfItsOwnType() {
        RecordingBus bus = new RecordingBus();
        List<TickEvent> received = new ArrayList<>();
        bus.subscribe(TickEvent.class, received::add);

        bus.publish(new TickEvent(42));

        assertEquals(List.of(new TickEvent(42)), received, "the exact published event must reach the subscriber");
    }

    @Test
    void handlerSubscribedToADifferentEventTypeIsNeverCalled() {
        RecordingBus bus = new RecordingBus();
        List<Object> wrongTypeReceived = new ArrayList<>();
        bus.subscribe(WorldInitEvent.class, wrongTypeReceived::add);

        bus.publish(new TickEvent(1));

        assertTrue(wrongTypeReceived.isEmpty(), "a TickEvent must not reach a WorldInitEvent subscriber");
    }

    @Test
    void multipleSubscribersToTheSameEventTypeAllRun() {
        RecordingBus bus = new RecordingBus();
        List<String> calls = new ArrayList<>();
        bus.subscribe(BuildingPlacedEvent.class, e -> calls.add("first"));
        bus.subscribe(BuildingPlacedEvent.class, e -> calls.add("second"));

        bus.publish(new BuildingPlacedEvent(com.rustorio.api.content.ContentId.of("rustorio:miner"), 3, 4));

        assertEquals(List.of("first", "second"), calls, "every subscriber to the same event type must run, in subscription order");
    }
}
