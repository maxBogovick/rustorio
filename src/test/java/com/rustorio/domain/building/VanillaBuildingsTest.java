package com.rustorio.domain.building;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.VanillaTechs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertSame(PlacementRule.NEEDS_ORE, prototypeFor(BuildingType.ELECTRIC_MINER).placementRule());
        assertSame(PlacementRule.ALWAYS, prototypeFor(BuildingType.UNDERGROUND_IN).placementRule());
        assertSame(PlacementRule.ALWAYS, prototypeFor(BuildingType.UNDERGROUND_OUT).placementRule());
        assertSame(PlacementRule.ADJACENT_TO_WATER, prototypeFor(BuildingType.PUMP).placementRule());
        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.MINER || type == BuildingType.ELECTRIC_MINER
                    || type == BuildingType.UNDERGROUND_IN
                    || type == BuildingType.UNDERGROUND_OUT || type == BuildingType.PUMP) {
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

    /** {@code label} is read straight off {@link BuildingType#label()} — see {@link VanillaBuildings#register}'s own comment on why. */
    @Test
    void labelMatchesBuildingTypesOwnLabel() {
        for (BuildingType type : BuildingType.values()) {
            assertEquals(type.label(), prototypeFor(type).label(), type + "'s prototype label must match its own BuildingType#label()");
        }
    }

    /** {@code footprintWidth}/{@code footprintHeight} are read off {@link BuildingType#footprintWidth()}/{@link BuildingType#footprintHeight()} — 2x2 for ASSEMBLER, 1x1 for everything else. */
    @Test
    void footprintMatchesBuildingTypesOwnFootprint() {
        for (BuildingType type : BuildingType.values()) {
            assertEquals(type.footprintWidth(), prototypeFor(type).footprintWidth(), type + "'s prototype footprintWidth");
            assertEquals(type.footprintHeight(), prototypeFor(type).footprintHeight(), type + "'s prototype footprintHeight");
        }
        assertEquals(2, prototypeFor(BuildingType.ASSEMBLER).footprintWidth());
        assertEquals(2, prototypeFor(BuildingType.ASSEMBLER).footprintHeight());
        assertEquals(1, prototypeFor(BuildingType.MINER).footprintWidth());
        assertEquals(1, prototypeFor(BuildingType.MINER).footprintHeight());
    }

    /**
     * The set {@code com.rustorio.mod.BuildingJsonLoader} consults before accepting a {@code
     * "power"} block — must match exactly the archetypes whose Java class calls {@link
     * BuildingPrototype#power()} at all ({@link Miner}, {@link Furnace}, {@link Pole}, {@link
     * Generator}), not a superset or subset of it.
     */
    @Test
    void honorsPowerIsTrueOnlyForArchetypesWhoseJavaBehaviorReadsIt() {
        assertTrue(VanillaBuildings.honorsPower(BuildingType.MINER));
        assertTrue(VanillaBuildings.honorsPower(BuildingType.ELECTRIC_MINER));
        assertTrue(VanillaBuildings.honorsPower(BuildingType.POLE));
        assertTrue(VanillaBuildings.honorsPower(BuildingType.GENERATOR));
        assertTrue(VanillaBuildings.honorsPower(BuildingType.FURNACE));
        assertTrue(VanillaBuildings.honorsPower(BuildingType.PRESS));
        assertTrue(VanillaBuildings.honorsPower(BuildingType.ASSEMBLER));
        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.MINER || type == BuildingType.ELECTRIC_MINER
                    || type == BuildingType.POLE || type == BuildingType.GENERATOR
                    || type == BuildingType.FURNACE || type == BuildingType.PRESS
                    || type == BuildingType.ASSEMBLER) {
                continue;
            }
            assertFalse(VanillaBuildings.honorsPower(type), type + " must not claim to honor power");
        }
    }

    /**
     * Every archetype that has EVER had a hardcoded tech-speed gate keeps exactly that gate as its
     * own default trait — a JSON building reusing one of these and never mentioning {@code
     * "speedTech"} must see the identical vanilla behavior it always has (see
     * {@code com.rustorio.mod.BuildingJsonLoaderTest#omittingSpeedTechInheritsTheBorrowedArchetypesOwnDefault}
     * for the JSON-facing half of this same guarantee).
     */
    @Test
    void speedTechDefaultsMatchTheArchetypesThatHaveEverHadOne() {
        assertEquals(VanillaTechs.FAST_MINING, prototypeFor(BuildingType.MINER).speedTech());
        assertEquals(VanillaTechs.FAST_MINING, prototypeFor(BuildingType.ELECTRIC_MINER).speedTech());
        assertEquals(VanillaTechs.FAST_SMELTING, prototypeFor(BuildingType.FURNACE).speedTech());
        assertEquals(VanillaTechs.FAST_SMELTING, prototypeFor(BuildingType.PRESS).speedTech());
        assertEquals(VanillaTechs.FAST_SMELTING, prototypeFor(BuildingType.ASSEMBLER).speedTech());
        assertEquals(VanillaTechs.FAST_LAB, prototypeFor(BuildingType.LAB).speedTech());
        assertNull(prototypeFor(BuildingType.CHEST).speedTech(), "CHEST has never had a tech-speed gate");
        assertNull(prototypeFor(BuildingType.POLE).speedTech(), "a pole has no timing to speed up at all");
    }

    private static BuildingPrototype prototypeFor(BuildingType type) {
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type));
    }
}
