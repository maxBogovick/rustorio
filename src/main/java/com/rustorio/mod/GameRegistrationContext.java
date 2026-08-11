package com.rustorio.mod;

import com.rustorio.api.mod.MarkerTechEffect;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RegistryKeys;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.registry.RegistryKey;
import com.rustorio.api.content.vanilla.VanillaTechEffects;
import com.rustorio.domain.building.ServiceKey;
import com.rustorio.domain.building.VanillaPlacementRules;
import com.rustorio.domain.building.WorldServices;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * The one {@link RegistrationContext} instance shared by every mod across all three lifecycle
 * rounds (see {@link ModLoader}) — a later-loaded mod's round sees exactly what an earlier one
 * already registered, because every one of them writes into these same {@link Registry} instances.
 *
 * <p>A {@link LinkedHashMap} keyed by {@link RegistryKey}, not seven named fields: the seven were
 * the reason adding one content kind touched half a dozen files. {@code LinkedHashMap} rather than
 * {@code HashMap} because {@link #keys()} is what the loader freezes and reports in order, and an
 * order that changes per JVM run is the trap this repository keeps its rules about.
 *
 * <p>The unchecked cast in {@link #registry} is the standard typesafe-heterogeneous-container trade
 * (Effective Java, Item 33): {@link #register} is the only writer and it only ever stores a {@code
 * Registry<T>} under a {@code RegistryKey<T>}, so the cast cannot fail unless someone bypasses it.
 */
final class GameRegistrationContext implements RegistrationContext {

    private final Map<RegistryKey<?>, Registry<?>> registries = new LinkedHashMap<>();

    /** Filled by {@link #registerService}; handed to {@code LoadedGame} as a factory, not as instances — see {@link WorldServices.Builder}. */
    private final WorldServices.Builder services = WorldServices.builder();

    /**
     * Starts with a registry for every key the base game ships — see {@link RegistryKeys#VANILLA}
     * for why that list has a fixed order.
     *
     * <p>Placement rules arrive already filled, unlike every other registry, which starts empty and
     * is filled from a mod's {@code content/} directory. A rule is a function of a cell rather than
     * data, so there is no JSON for one to arrive through. Marker tech-effect ids are seeded here
     * the same way (see {@link #seedVanillaTechEffects}) so a technology JSON can name them before
     * any code mod runs. Seeding here rather than in {@link ModLoader} means every context has
     * them, including one a test builds directly, and a mod's own round still runs afterwards and
     * can add to or replace them.
     */
    GameRegistrationContext() {
        for (RegistryKey<?> key : RegistryKeys.VANILLA) {
            registries.put(key, new Registry<>());
        }
        VanillaPlacementRules.registerAll(placementRules());
        seedVanillaTechEffects();
    }

    /** Marker effects for the five vanilla techs — lives here so {@code domain} never imports {@code api.mod}. */
    private void seedVanillaTechEffects() {
        techEffects().register(VanillaTechEffects.FAST_MINING,
                new MarkerTechEffect(VanillaTechEffects.FAST_MINING, "Faster mining"));
        techEffects().register(VanillaTechEffects.FAST_SMELTING,
                new MarkerTechEffect(VanillaTechEffects.FAST_SMELTING, "Faster smelting"));
        techEffects().register(VanillaTechEffects.BIG_BUFFER,
                new MarkerTechEffect(VanillaTechEffects.BIG_BUFFER, "Bigger buffers"));
        techEffects().register(VanillaTechEffects.LONG_TUNNEL,
                new MarkerTechEffect(VanillaTechEffects.LONG_TUNNEL, "Longer tunnels"));
        techEffects().register(VanillaTechEffects.FAST_LAB,
                new MarkerTechEffect(VanillaTechEffects.FAST_LAB, "Faster research"));
    }

    @Override
    @SuppressWarnings("unchecked") // see the class javadoc: register() is the only writer
    public <T> Registry<T> registry(RegistryKey<T> key) {
        Registry<?> registry = registries.get(key);
        if (registry == null) {
            throw new NoSuchElementException("no registry under key: " + key);
        }
        return (Registry<T>) registry;
    }

    /**
     * Add a registry for a content kind the base game does not ship — how a code mod holds its own
     * kind of content on the same footing as items and buildings, including being frozen and
     * reported alongside them.
     */
    <T> void register(RegistryKey<T> key, Registry<T> registry) {
        registries.put(key, registry);
    }

    /** Every registered key, in declaration order — what {@link ModLoader} freezes and reports over. */
    Iterable<RegistryKey<?>> keys() {
        return registries.keySet();
    }

    /**
     * See {@link RegistrationContext#registerService}. Collected into a builder rather than a live
     * {@link com.rustorio.domain.building.WorldServices} because, exactly like the registries above,
     * a later mod's round must be able to overwrite what an earlier one put here — freezing happens
     * once, at the end, in {@link ModLoader}.
     */
    @Override
    public <T> void registerService(ServiceKey<T> key, Supplier<T> provider) {
        services.with(key, provider);
    }

    /** The provider set {@link #registerService} collected — {@code LoadedGame} keeps it and builds fresh instances per world. */
    WorldServices.Builder serviceProviders() {
        return services;
    }
}
