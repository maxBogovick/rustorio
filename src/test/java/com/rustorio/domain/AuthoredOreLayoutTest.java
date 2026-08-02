package com.rustorio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link AuthoredOreLayout}: the third {@link OreLayout} implementation, built from a mod author's
 * own {@link AuthoredMap} (the content editor's canvas) instead of hardcoded Java or a seed. Proves
 * it honors the SAME rasterization contract {@link PatchOreLayout}/{@link RandomOreLayout} already
 * do — first patch in list order wins an overlap, ore always wins over terrain — since a mod author
 * painting overlapping circles in the editor needs the exact same result the built-in map gives.
 */
class AuthoredOreLayoutTest {

    private static final ContentId MAP_ID = new ContentId("testmod", "example");

    @Test
    void reportsOreAndTerrainFromTheirOwnPatches() {
        AuthoredMap map = new AuthoredMap(MAP_ID,
                List.of(new OrePatch(10, 10, 3, VanillaItems.IRON_ORE)),
                List.of(new TerrainPatch(50, 50, 4, Terrain.WATER)));
        AuthoredOreLayout layout = AuthoredOreLayout.from(map);

        assertEquals(Optional.of(VanillaItems.IRON_ORE), layout.oreAt(10, 10));
        assertEquals(Terrain.WATER, layout.terrainAt(50, 50));
        assertEquals(Terrain.GROUND, layout.terrainAt(0, 0), "an unpainted cell is plain buildable ground");
        assertFalse(layout.hasOre(0, 0));
    }

    @Test
    void firstPatchInListOrderWinsAnOverlap() {
        AuthoredMap map = new AuthoredMap(MAP_ID,
                List.of(new OrePatch(10, 10, 5, VanillaItems.IRON_ORE), new OrePatch(12, 10, 5, VanillaItems.COAL)),
                List.of());
        AuthoredOreLayout layout = AuthoredOreLayout.from(map);

        assertEquals(Optional.of(VanillaItems.IRON_ORE), layout.oreAt(12, 10),
                "the FIRST patch declared must win the cell both circles cover, not the second");
    }

    @Test
    void oreAlwaysWinsOverTerrainEvenWhenTerrainIsDeclaredFirst() {
        AuthoredMap map = new AuthoredMap(MAP_ID,
                List.of(new OrePatch(20, 20, 4, VanillaItems.BRONZE_ORE)),
                List.of(new TerrainPatch(20, 20, 4, Terrain.ROCK)));
        AuthoredOreLayout layout = AuthoredOreLayout.from(map);

        assertEquals(Optional.of(VanillaItems.BRONZE_ORE), layout.oreAt(20, 20));
        assertTrue(layout.isPassable(20, 20), "an ore cell must never end up reported as impassable rock/water");
    }

    @Test
    void idNamesTheAuthoredMapItWasBuiltFrom() {
        AuthoredMap map = new AuthoredMap(MAP_ID, List.of(), List.of());
        AuthoredOreLayout layout = AuthoredOreLayout.from(map);

        assertEquals("authored:testmod:example", layout.id().kind());
        assertEquals(PatchOreLayout.STANDARD_WIDTH, layout.id().width());
        assertEquals(PatchOreLayout.STANDARD_HEIGHT, layout.id().height());
    }

    @Test
    void eachFromCallIsAFreshUndepletedInstance() {
        AuthoredMap map = new AuthoredMap(MAP_ID, List.of(new OrePatch(5, 5, 2, VanillaItems.IRON_ORE)), List.of());
        AuthoredOreLayout first = AuthoredOreLayout.from(map);
        first.extract(5, 5);

        AuthoredOreLayout second = AuthoredOreLayout.from(map);

        assertTrue(second.depletionSnapshot().isEmpty(),
                "a fresh AuthoredOreLayout must never inherit depletion from a previous one built off the same AuthoredMap");
    }
}
