package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.OreLayoutId;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (X-02, DEV_TASKS.md) {@link PlacementRule}: terrain now gates every building except a tunnel,
 * and a miner needs BOTH passable terrain and ore, not just ore — a fixed, hand-built {@link
 * OreLayout} double gives full control over the (terrain, ore) combination at one cell, rather
 * than hunting for one in a real generated map. Reads each type's rule off {@link
 * VanillaBuildings} (data), not a {@code switch} — {@code PlacementRule.forType} no longer exists.
 */
class PlacementRuleTest {

    private static PlacementRule ruleFor(BuildingType type) {
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type)).placementRule();
    }

    private static OreLayout layoutAt(int atX, int atY, @Nullable ItemType terrain, @Nullable ItemType ore) {
        return new OreLayout() {
            @Override
            public Optional<ItemType> oreAt(int x, int y) {
                return x == atX && y == atY ? Optional.ofNullable(ore) : Optional.empty();
            }

            @Override
            public Optional<ItemType> extract(int x, int y) {
                return oreAt(x, y);
            }

            @Override
            public OreLayoutId id() {
                return new OreLayoutId("test", 0, 0, 0);
            }

            @Override
            public Optional<ItemType> terrainAt(int x, int y) {
                return x == atX && y == atY ? Optional.ofNullable(terrain) : Optional.empty();
            }

            @Override
            public Map<Integer, Integer> depletionSnapshot() {
                return Map.of();
            }

            @Override
            public void restoreDepletion(Map<Integer, Integer> snapshot) {
                // not exercised by this test double — PlacementRule never touches depletion
            }
        };
    }

    @Test
    void mostBuildingsRefuseWaterOrRock() {
        OreLayout water = layoutAt(0, 0, VanillaItems.WATER, null);
        OreLayout rock = layoutAt(0, 0, VanillaItems.ROCK, null);

        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.UNDERGROUND_IN || type == BuildingType.UNDERGROUND_OUT) {
                continue; // covered separately below
            }
            assertFalse(ruleFor(type).test(0, 0, water), type + " must refuse water");
            assertFalse(ruleFor(type).test(0, 0, rock), type + " must refuse rock");
        }
    }

    @Test
    void mostBuildingsAcceptPassableGroundRegardlessOfOre() {
        OreLayout bareGround = layoutAt(0, 0, null, null);

        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.MINER || type == BuildingType.ELECTRIC_MINER
                    || type == BuildingType.PUMP) {
                continue; // all three additionally need something nearby — covered separately below
            }
            assertTrue(ruleFor(type).test(0, 0, bareGround), type + " must accept plain passable ground");
        }
    }

    @Test
    void pumpNeedsPassableGroundWithWaterNextToIt() {
        // The pump's own cell is dry ground; the cell to its left is the water it reaches into.
        OreLayout shore = layoutAt(-1, 0, VanillaItems.WATER, null);
        OreLayout inland = layoutAt(5, 5, VanillaItems.WATER, null);
        OreLayout onTheWaterItself = layoutAt(0, 0, VanillaItems.WATER, null);

        assertTrue(ruleFor(BuildingType.PUMP).test(0, 0, shore));
        assertFalse(ruleFor(BuildingType.PUMP).test(0, 0, inland),
                "dry ground with no water within reach — a pump here would lift nothing");
        assertFalse(ruleFor(BuildingType.PUMP).test(0, 0, onTheWaterItself),
                "a pump stands on the shore, not on the water: its own cell still has to be buildable");
    }

    @Test
    void tunnelsIgnoreTerrainEntirely() {
        OreLayout water = layoutAt(0, 0, VanillaItems.WATER, null);
        OreLayout rock = layoutAt(0, 0, VanillaItems.ROCK, null);

        assertTrue(ruleFor(BuildingType.UNDERGROUND_IN).test(0, 0, water));
        assertTrue(ruleFor(BuildingType.UNDERGROUND_OUT).test(0, 0, rock));
    }

    @Test
    void minerNeedsBothPassableTerrainAndOre() {
        OreLayout groundWithOre = layoutAt(0, 0, null, VanillaItems.IRON_ORE);
        OreLayout groundNoOre = layoutAt(0, 0, null, null);
        OreLayout waterWithOre = layoutAt(0, 0, VanillaItems.WATER, VanillaItems.IRON_ORE);

        assertTrue(ruleFor(BuildingType.MINER).test(0, 0, groundWithOre));
        assertTrue(ruleFor(BuildingType.ELECTRIC_MINER).test(0, 0, groundWithOre),
                "an electric miner is a miner as far as placement goes — only its power demand differs");
        assertFalse(ruleFor(BuildingType.MINER).test(0, 0, groundNoOre),
                "passable but no ore — a miner here would idle forever");
        assertFalse(ruleFor(BuildingType.MINER).test(0, 0, waterWithOre),
                "ore under water must still refuse — terrain gates a miner just like everything else");
    }
}
