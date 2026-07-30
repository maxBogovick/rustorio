package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import java.util.Locale;

/**
 * The game's own built-in building prototypes — one per {@link BuildingType} constant, matching
 * today's {@code BuildingCost.forType}/{@code PlacementRule.forType}/{@code
 * Textures.forBuildingType} number-for-number. {@link #frozen()} is the shared, already-frozen
 * {@link Registry} that code without an injected one falls back to — same role {@link
 * VanillaItems#frozen()} plays for items.
 */
public final class VanillaBuildings {

    // FROZEN must be declared (and therefore initialized) before anything that calls frozen() —
    // same top-to-bottom initialization-order trap already caught once in VanillaItems.
    private static final Registry<BuildingPrototype> FROZEN = buildFrozen();

    private VanillaBuildings() {
    }

    /**
     * {@code BuildingType}'s constant name, lowercased, under the {@code rustorio} namespace — the
     * one place this conversion happens, so {@link #registerAll} and any later lookup (a future
     * card) always agree on the same id instead of each retyping the convention independently.
     */
    public static ContentId idFor(BuildingType type) {
        return new ContentId("rustorio", type.name().toLowerCase(Locale.ROOT));
    }

    /** The canonical, already-frozen registry backing every building's default data. */
    public static Registry<BuildingPrototype> frozen() {
        return FROZEN;
    }

    /**
     * Registers all 12 vanilla building prototypes into {@code prototypes}. For tests/custom
     * assemblies that want their own isolated (unfrozen) copy instead of sharing {@link #frozen()}.
     *
     * <p>{@code acceptsSpeedEffects} is {@code true} exactly for the six kinds {@code
     * UpgradeSpeedAction} already accepts today ({@code MINER}/{@code CHEST}/{@code FURNACE}/
     * {@code PRESS}/{@code ASSEMBLER}/{@code LAB}) — {@code CHEST} included, not an oversight:
     * {@link Chest#tick} genuinely pushes one item per tick, so doubling that call is a real speed
     * effect, unlike the six single-slot/segment-joining kinds refused below (each doubles a {@code
     * tick()} call that provably does nothing the second time — see {@code UpgradeSpeedAction}'s
     * own javadoc).
     */
    public static void registerAll(Registry<BuildingPrototype> prototypes) {
        register(prototypes, BuildingType.MINER, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_ORE, VanillaSprites.MINER, true);
        register(prototypes, BuildingType.CHEST, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.CHEST, true);
        registerFurnaceLike(prototypes, BuildingType.FURNACE, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                VanillaSprites.FURNACE_COLD, 5, 1);
        register(prototypes, BuildingType.BELT, new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.BELT_EMPTY, false);
        register(prototypes, BuildingType.SPLITTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.SPLITTER, false);
        registerFurnaceLike(prototypes, BuildingType.PRESS, new BuildingCost(VanillaItems.IRON_PLATE, 8),
                VanillaSprites.FURNACE_COLD, 5, 1);
        register(prototypes, BuildingType.UNDERGROUND_IN, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_IN, false);
        register(prototypes, BuildingType.UNDERGROUND_OUT, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_OUT, false);
        register(prototypes, BuildingType.LAB, new BuildingCost(VanillaItems.GEAR, 10),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.LAB, true);
        register(prototypes, BuildingType.FILTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.FILTER, false);
        register(prototypes, BuildingType.INSERTER, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.INSERTER, false);
        registerFurnaceLike(prototypes, BuildingType.ASSEMBLER, new BuildingCost(VanillaItems.GEAR, 15),
                VanillaSprites.ASSEMBLER, 5, 1);
    }

    private static Registry<BuildingPrototype> buildFrozen() {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        registerAll(prototypes);
        prototypes.freeze();
        return prototypes;
    }

    /** Every non-{@link Furnace} archetype: {@code bufferMax}/{@code speedMultiplier} are meaningless to it, registered as {@code 0}/{@code 1}. */
    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, boolean acceptsSpeedEffects) {
        register(prototypes, type, cost, placementRule, texture, 0, 1, acceptsSpeedEffects);
    }

    /**
     * A {@link Furnace}-kind archetype (also {@code PRESS}/{@code ASSEMBLER}, which reuse the same
     * class) — always {@link PlacementRule#NEEDS_PASSABLE_TERRAIN}, same as every non-tunnel/miner
     * building, and always accepts speed effects — every {@link Furnace}-kind building does today.
     */
    private static void registerFurnaceLike(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, ContentId texture, int bufferMax, int speedMultiplier) {
        register(prototypes, type, cost, PlacementRule.NEEDS_PASSABLE_TERRAIN, texture, bufferMax, speedMultiplier, true);
    }

    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects) {
        ContentId id = idFor(type);
        prototypes.register(id,
                new BuildingPrototype(id, cost, placementRule, texture, bufferMax, speedMultiplier, acceptsSpeedEffects));
    }
}
