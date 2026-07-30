package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.Terrain;
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
 * than hunting for one in a real generated map.
 */
class PlacementRuleTest {

    private static OreLayout layoutAt(int atX, int atY, Terrain terrain, @Nullable ItemType ore) {
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
            public Terrain terrainAt(int x, int y) {
                return x == atX && y == atY ? terrain : Terrain.GROUND;
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
        OreLayout water = layoutAt(0, 0, Terrain.WATER, null);
        OreLayout rock = layoutAt(0, 0, Terrain.ROCK, null);

        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.UNDERGROUND_IN || type == BuildingType.UNDERGROUND_OUT) {
                continue; // covered separately below
            }
            assertFalse(PlacementRule.forType(type).test(0, 0, water), type + " must refuse water");
            assertFalse(PlacementRule.forType(type).test(0, 0, rock), type + " must refuse rock");
        }
    }

    @Test
    void mostBuildingsAcceptPassableGroundRegardlessOfOre() {
        OreLayout bareGround = layoutAt(0, 0, Terrain.GROUND, null);

        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.MINER) {
                continue; // a miner additionally needs ore — covered separately below
            }
            assertTrue(PlacementRule.forType(type).test(0, 0, bareGround), type + " must accept plain passable ground");
        }
    }

    @Test
    void tunnelsIgnoreTerrainEntirely() {
        OreLayout water = layoutAt(0, 0, Terrain.WATER, null);
        OreLayout rock = layoutAt(0, 0, Terrain.ROCK, null);

        assertTrue(PlacementRule.forType(BuildingType.UNDERGROUND_IN).test(0, 0, water));
        assertTrue(PlacementRule.forType(BuildingType.UNDERGROUND_OUT).test(0, 0, rock));
    }

    @Test
    void minerNeedsBothPassableTerrainAndOre() {
        OreLayout groundWithOre = layoutAt(0, 0, Terrain.GROUND, VanillaItems.IRON_ORE);
        OreLayout groundNoOre = layoutAt(0, 0, Terrain.GROUND, null);
        OreLayout waterWithOre = layoutAt(0, 0, Terrain.WATER, VanillaItems.IRON_ORE);

        assertTrue(PlacementRule.forType(BuildingType.MINER).test(0, 0, groundWithOre));
        assertFalse(PlacementRule.forType(BuildingType.MINER).test(0, 0, groundNoOre),
                "passable but no ore — a miner here would idle forever");
        assertFalse(PlacementRule.forType(BuildingType.MINER).test(0, 0, waterWithOre),
                "ore under water must still refuse — terrain gates a miner just like everything else");
    }
}
