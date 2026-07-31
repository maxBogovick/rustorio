package com.rustorio.api.mod;

import java.util.function.Consumer;

/**
 * Publish/subscribe by event type — what a mod's {@link RustorioMod#subscribeEvents} receives.
 * Deliberately keyed on {@link Class} rather than a hand-rolled enum of event kinds: adding a new
 * event type (a later phase, or a mod's own convention built on top) never means editing a
 * registry of "known" kinds here.
 *
 * <p>See {@code com.rustorio.mod}'s implementation for what actually calls {@link #publish} and
 * when — this interface only states the contract a mod programs against.
 */
public interface EventBus {

    /** Registers {@code handler} to run every time an event assignable to {@code eventType} is published. */
    <E> void subscribe(Class<E> eventType, Consumer<E> handler);

    /** Runs every handler subscribed to {@code event}'s own runtime type. */
    <E> void publish(E event);
}
