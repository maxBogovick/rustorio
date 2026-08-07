package com.rustorio.domain.building;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Every {@link ServiceKey} a world was built with, and what answers it — what {@link
 * TickContext#service} reads. Built once, at world construction, and never written afterwards: a
 * building asks for a service, it never installs one.
 *
 * <p>{@link LinkedHashMap}, not {@code Map.of}: {@link #closeAll} iterates this, and a shutdown
 * order that differs between JVM runs is exactly the kind of thing this project keeps a rule
 * about — a service whose {@code close} depends on another's having run first would fail on
 * roughly half the runs and pass on the rest.
 *
 * <p>The unchecked cast in {@link #get} is the standard typesafe-heterogeneous-container trade
 * (Effective Java, Item 33): {@link Builder#with} is the only writer and it only ever stores a
 * {@code T} under a {@code ServiceKey<T>}, so the cast cannot fail unless someone bypasses it.
 */
public final class WorldServices {

    /** A world with no services at all — what every test, dev tool and vanilla-only game gets. */
    public static final WorldServices NONE = new WorldServices(Map.of());

    private final Map<ServiceKey<?>, Object> byKey;

    private WorldServices(Map<ServiceKey<?>, Object> byKey) {
        this.byKey = byKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * What answers {@code key} here, or empty if this world has no such service — empty is the
     * ORDINARY case, not an error: a building whose mod is loaded but whose service was never
     * wired (a headless test, a dev tool) must degrade the way a miner on a cell with no ore does,
     * not throw.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(ServiceKey<T> key) {
        return Optional.ofNullable((T) byKey.get(key));
    }

    /**
     * Closes every service that is {@link AutoCloseable}, in registration order — how a background
     * thread pool a mod started gets shut down when the game does, without the shutting-down code
     * ({@code com.graphics.screen.GameScreen}) having to know which mods started what.
     *
     * <p>Keeps going after a failure rather than propagating the first one: a service that throws
     * on close must not leave the remaining ones running, and shutdown has nobody left to report to
     * anyway.
     */
    public void closeAll() {
        for (Object service : byKey.values()) {
            if (service instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    // Deliberately swallowed — see this method's own javadoc.
                }
            }
        }
    }

    /**
     * Collects service PROVIDERS during load, and builds a fresh set of instances per world.
     *
     * <p>Providers rather than instances, and the distinction is not academic: services used to be
     * registered as ready-made objects, which made one instance shared by every world built from
     * the same loaded game. Closing one world then shut that instance down for all the others —
     * leaving the main menu and starting a second game crashed on the first tick with {@code
     * RejectedExecutionException} from a thread pool the PREVIOUS game had already closed. Shared
     * per-cell state had the same shape: a new game inherited the old one's leftovers.
     *
     * <p>A mod that genuinely wants one shared instance can still have it — its provider can return
     * the same object every time. That is now an explicit choice rather than the only option.
     */
    public static final class Builder {

        private final Map<ServiceKey<?>, Supplier<?>> providersByKey = new LinkedHashMap<>();

        private Builder() {
        }

        /** Registers a provider under {@code key}, replacing whatever was there — a later mod deliberately CAN override an earlier one's service, same as it can {@code update} another's content. */
        public <T> Builder with(ServiceKey<T> key, Supplier<T> provider) {
            providersByKey.put(key, provider);
            return this;
        }

        /** A fresh set of service instances, one per registered provider — called once per {@code World}. */
        public WorldServices build() {
            if (providersByKey.isEmpty()) {
                return NONE;
            }
            Map<ServiceKey<?>, Object> instances = new LinkedHashMap<>();
            providersByKey.forEach((key, provider) -> instances.put(key, provider.get()));
            return new WorldServices(instances);
        }
    }
}
