package com.rustorio.mod;

import com.rustorio.api.mod.EventBus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The straightforward {@link EventBus} implementation: a handler list keyed by the exact event class, in subscription order. */
final class SimpleEventBus implements EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> handlersByType = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <E> void subscribe(Class<E> eventType, Consumer<E> handler) {
        handlersByType.computeIfAbsent(eventType, type -> new ArrayList<>()).add((Consumer<Object>) handler);
    }

    @Override
    public <E> void publish(E event) {
        List<Consumer<Object>> handlers = handlersByType.get(event.getClass());
        if (handlers != null) {
            for (Consumer<Object> handler : handlers) {
                handler.accept(event);
            }
        }
    }
}
