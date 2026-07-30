package com.rustorio.domain.building;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link VanillaBuildings}: every {@link BuildingType} gets a registered {@link BuildingPrototype}
 * carrying its cost/placement-rule/texture as plain data — the only place these numbers are typed
 * now, {@code BuildingCost.forType}/{@code PlacementRule.forType}/{@code Textures.forBuildingType}'s
 * data-`switch` half no longer exist. Expected values below are the same numbers those switches
 * used to hold, transcribed once here rather than mirrored against a now-deleted method.
 */
class VanillaBuildingsTest {

    @Test
    void registersExactlyOnePrototypePerBuildingType() {
        Registry<BuildingPrototype> prototypes = VanillaBuildings.frozen();

        assertEquals(BuildingType.values().length, prototypes.size());
    }

    @Test
    void costMatchesTheOwnersBalancePass() {
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 5), prototypeFor(BuildingType.MINER).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 5), prototypeFor(BuildingType.CHEST).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 5), prototypeFor(BuildingType.FURNACE).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 1), prototypeFor(BuildingType.BELT).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 3), prototypeFor(BuildingType.SPLITTER).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 3), prototypeFor(BuildingType.FILTER).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 2), prototypeFor(BuildingType.INSERTER).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 2), prototypeFor(BuildingType.UNDERGROUND_IN).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 2), prototypeFor(BuildingType.UNDERGROUND_OUT).cost());
        assertEquals(new BuildingCost(VanillaItems.IRON_PLATE, 8), prototypeFor(BuildingType.PRESS).cost());
        assertEquals(new BuildingCost(VanillaItems.GEAR, 10), prototypeFor(BuildingType.LAB).cost());
        assertEquals(new BuildingCost(VanillaItems.GEAR, 15), prototypeFor(BuildingType.ASSEMBLER).cost());
    }

    @Test
    void placementRuleMatchesTheOwnerDecisionOnTerrain() {
        assertSame(PlacementRule.NEEDS_ORE, prototypeFor(BuildingType.MINER).placementRule());
        assertSame(PlacementRule.ALWAYS, prototypeFor(BuildingType.UNDERGROUND_IN).placementRule());
        assertSame(PlacementRule.ALWAYS, prototypeFor(BuildingType.UNDERGROUND_OUT).placementRule());
        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.MINER || type == BuildingType.UNDERGROUND_IN || type == BuildingType.UNDERGROUND_OUT) {
                continue;
            }
            assertSame(PlacementRule.NEEDS_PASSABLE_TERRAIN, prototypeFor(type).placementRule(),
                    type + " must need passable terrain, nothing more");
        }
    }

    /**
     * Transcribed by hand from {@code Textures.forBuildingType}'s former switch (now deleted,
     * replaced by reading this same field) — {@code Textures} itself can't be constructed headless,
     * same reason {@code VanillaItemsTest} transcribes colors/shapes from {@code Palette} instead
     * of calling it.
     */
    @Test
    void textureMatchesTexturesForBuildingType() {
        assertEquals(VanillaSprites.MINER, prototypeFor(BuildingType.MINER).texture());
        assertEquals(VanillaSprites.CHEST, prototypeFor(BuildingType.CHEST).texture());
        assertEquals(VanillaSprites.FURNACE_COLD, prototypeFor(BuildingType.FURNACE).texture());
        assertEquals(VanillaSprites.BELT_EMPTY, prototypeFor(BuildingType.BELT).texture());
        assertEquals(VanillaSprites.SPLITTER, prototypeFor(BuildingType.SPLITTER).texture());
        assertEquals(VanillaSprites.FURNACE_COLD, prototypeFor(BuildingType.PRESS).texture());
        assertEquals(VanillaSprites.UNDERGROUND_IN, prototypeFor(BuildingType.UNDERGROUND_IN).texture());
        assertEquals(VanillaSprites.UNDERGROUND_OUT, prototypeFor(BuildingType.UNDERGROUND_OUT).texture());
        assertEquals(VanillaSprites.LAB, prototypeFor(BuildingType.LAB).texture());
        assertEquals(VanillaSprites.FILTER, prototypeFor(BuildingType.FILTER).texture());
        assertEquals(VanillaSprites.INSERTER, prototypeFor(BuildingType.INSERTER).texture());
        assertEquals(VanillaSprites.ASSEMBLER, prototypeFor(BuildingType.ASSEMBLER).texture());
    }

    private static BuildingPrototype prototypeFor(BuildingType type) {
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type));
    }
}
