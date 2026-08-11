package com.rustorio.api.content.vanilla;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.FluidType;
import com.rustorio.api.registry.Registry;

/**
 * The game's own built-in fluids — the {@link FluidType} counterpart to {@link VanillaItems}, with
 * the same two-stage shape: {@link #registerAll} for a caller assembling its own registry, {@link
 * #frozen()} for everything that just wants the shared, already-frozen one.
 *
 * <p>Only the two the vanilla chain needs. Water is what a pump lifts out of a river and steam is
 * what a boiler makes of it; both exist here rather than in a mod because the vanilla game itself
 * is expressed as data in {@code resources/mods/rustorio}, and that mod's content has to match
 * this class number-for-number (see {@code VanillaAsModParityTest}).
 */
public final class VanillaFluids {

    // Declared before FROZEN and before the public constants below: Java runs static initializers
    // in textual order, and a constant that called frozen() from above it would read a null FROZEN
    // — the same trap VanillaItems documents and was caught by once.
    private static final ContentId WATER_ID = ContentId.of("rustorio:water");
    private static final ContentId STEAM_ID = ContentId.of("rustorio:steam");

    private static final Registry<FluidType> FROZEN = buildFrozen();

    public static final FluidType WATER = frozen().get(WATER_ID);
    public static final FluidType STEAM = frozen().get(STEAM_ID);

    private VanillaFluids() {
    }

    /** The canonical, already-frozen registry every caller without an injected one falls back to. */
    public static Registry<FluidType> frozen() {
        return FROZEN;
    }

    /** Registers both built-in fluids into {@code fluids} — for a test or assembly that wants its own unfrozen copy instead of sharing {@link #frozen()}. */
    public static void registerAll(Registry<FluidType> fluids) {
        fluids.register(WATER_ID, new FluidType(WATER_ID, "Water", 0x2E6FB7));
        fluids.register(STEAM_ID, new FluidType(STEAM_ID, "Steam", 0xD5DEE4));
    }

    private static Registry<FluidType> buildFrozen() {
        Registry<FluidType> fluids = new Registry<>();
        registerAll(fluids);
        fluids.freeze();
        return fluids;
    }
}
