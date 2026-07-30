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

    /** Registers all 12 vanilla building prototypes into {@code prototypes}. For tests/custom assemblies that want their own isolated (unfrozen) copy instead of sharing {@link #frozen()}. */
    public static void registerAll(Registry<BuildingPrototype> prototypes) {
        register(prototypes, BuildingType.MINER, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_ORE, VanillaSprites.MINER);
        register(prototypes, BuildingType.CHEST, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.CHEST);
        register(prototypes, BuildingType.FURNACE, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.FURNACE_COLD);
        register(prototypes, BuildingType.BELT, new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.BELT_EMPTY);
        register(prototypes, BuildingType.SPLITTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.SPLITTER);
        register(prototypes, BuildingType.PRESS, new BuildingCost(VanillaItems.IRON_PLATE, 8),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.FURNACE_COLD);
        register(prototypes, BuildingType.UNDERGROUND_IN, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_IN);
        register(prototypes, BuildingType.UNDERGROUND_OUT, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_OUT);
        register(prototypes, BuildingType.LAB, new BuildingCost(VanillaItems.GEAR, 10),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.LAB);
        register(prototypes, BuildingType.FILTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.FILTER);
        register(prototypes, BuildingType.INSERTER, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.INSERTER);
        register(prototypes, BuildingType.ASSEMBLER, new BuildingCost(VanillaItems.GEAR, 15),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.ASSEMBLER);
    }

    private static Registry<BuildingPrototype> buildFrozen() {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        registerAll(prototypes);
        prototypes.freeze();
        return prototypes;
    }

    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture) {
        ContentId id = idFor(type);
        prototypes.register(id, new BuildingPrototype(id, cost, placementRule, texture));
    }
}
