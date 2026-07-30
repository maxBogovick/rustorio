package com.rustorio.domain;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PatchOreLayout}: the fixed built-in map, plus (D-04, DEV_TASKS.md) its per-cell ore
 * depletion. The depletion RULE itself ({@link OreDepletion}) is exercised in depth by {@link
 * RandomOreLayoutTest} — these checks cover what's specific to this class: that {@link
 * #standard()} hands out an independent map every call, not a shared cached one.
 */
class PatchOreLayoutTest {

    /**
     * The fix for D-04's own risk: {@code standard()} used to cache one singleton instance before
     * depletion existed, which was harmless while {@code oreAt} was a pure function. Once {@code
     * extract} made this class stateful, a shared singleton would have let one {@code World}'s
     * mining silently thin out ore for every OTHER {@code World} built afterward in the same JVM —
     * exactly the failure mode a shared test process would hit first.
     */
    @Test
    void standardReturnsAFreshIndependentMapEveryCall() {
        PatchOreLayout first = PatchOreLayout.standard();
        int[] oreCell = findAnOreCell(first);

        for (int i = 0; i < OreDepletion.RICHNESS + OreDepletion.TAIL_INTERVAL * 3; i++) {
            first.extract(oreCell[0], oreCell[1]); // exhaust this instance's reserve into its tail
        }

        PatchOreLayout second = PatchOreLayout.standard();
        assertTrue(second.extract(oreCell[0], oreCell[1]).isPresent(),
                "a freshly obtained standard() map must not inherit another instance's depletion");
    }

    @Test
    void extractYieldsEveryCallWhileWithinRichness() {
        PatchOreLayout layout = PatchOreLayout.standard();
        int[] oreCell = findAnOreCell(layout);

        for (int i = 0; i < OreDepletion.RICHNESS; i++) {
            assertTrue(layout.extract(oreCell[0], oreCell[1]).isPresent(),
                    "call " + i + " should still be within the cell's undepleted reserve");
        }
    }

    @Test
    void extractNeverChangesWhatOreAtReports() {
        PatchOreLayout layout = PatchOreLayout.standard();
        int[] oreCell = findAnOreCell(layout);
        var before = layout.oreAt(oreCell[0], oreCell[1]);

        for (int i = 0; i < OreDepletion.RICHNESS + OreDepletion.TAIL_INTERVAL * 3; i++) {
            layout.extract(oreCell[0], oreCell[1]);
        }

        assertEquals(before, layout.oreAt(oreCell[0], oreCell[1]));
    }

    /** (X-04, DEV_TASKS.md) The map is 256x256, and it's a single constant — not two that could disagree. */
    @Test
    void standardMapIs256By256() {
        assertEquals(256, PatchOreLayout.STANDARD_WIDTH);
        assertEquals(256, PatchOreLayout.STANDARD_HEIGHT);

        PatchOreLayout layout = PatchOreLayout.standard();
        layout.oreAt(255, 255); // the far edge is still in bounds — must not throw
        assertEquals(Optional.empty(), layout.oreAt(256, 0), "one past the edge must be out of bounds");
        assertEquals(Optional.empty(), layout.oreAt(0, 256), "one past the edge must be out of bounds");
    }

    /** (X-02, DEV_TASKS.md) At least two obstacle kinds exist, and the known ore cell stays passable ground. */
    @Test
    void standardMapHasWaterAndRockObstacles() {
        PatchOreLayout layout = PatchOreLayout.standard();

        assertEquals(Terrain.WATER, layout.terrainAt(130, 90));
        assertEquals(Terrain.ROCK, layout.terrainAt(210, 150));
        assertTrue(layout.isPassable(6, 5), "the known iron ore cell must remain buildable ground");
    }

    /** (X-02, DEV_TASKS.md) Ore always wins — no cell reports both ore and impassable terrain. */
    @Test
    void noOreCellIsEverImpassable() {
        PatchOreLayout layout = PatchOreLayout.standard();

        for (int y = 0; y < 60; y++) {
            for (int x = 0; x < 92; x++) { // the region every ore patch sits within
                if (layout.hasOre(x, y)) {
                    assertTrue(layout.isPassable(x, y), "ore cell (" + x + "," + y + ") must never be impassable terrain");
                }
            }
        }
    }

    /** (D-05, DEV_TASKS.md) Coal is on the map and minable through the same {@code extract} path as iron/bronze. */
    @Test
    void standardMapHasMinableCoal() {
        PatchOreLayout layout = PatchOreLayout.standard();

        assertEquals(Optional.of(VanillaItems.COAL), layout.oreAt(2, 10));
        assertEquals(Optional.of(VanillaItems.COAL), layout.extract(2, 10));
        assertTrue(layout.isPassable(2, 10), "a coal cell must still be buildable ground, same as any other ore cell");
    }

    private static int[] findAnOreCell(PatchOreLayout layout) {
        // (6, 5) is the center of the standard map's first iron patch — same convention already
        // relied on by JsonSaveRepositoryTest/WorldTest.
        assertTrue(layout.hasOre(6, 5));
        return new int[] {6, 5};
    }
}
