package com.rustorio.domain;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RandomOreLayout}: the second {@link OreLayout} implementation, exercised for real by
 * {@code com.graphics.screen.GameScreen(long)} — these checks cover what that caller actually
 * relies on: a seed always yields the same map, and it isn't an empty one.
 */
class RandomOreLayoutTest {

    @Test
    void sameSeedYieldsTheExactSameMap() {
        RandomOreLayout a = new RandomOreLayout(42, 96, 64);
        RandomOreLayout b = new RandomOreLayout(42, 96, 64);

        for (int x = 0; x < 96; x += 4) {
            for (int y = 0; y < 64; y += 4) {
                assertEquals(a.oreAt(x, y), b.oreAt(x, y));
            }
        }
    }

    @Test
    void coversSomeButNotAllOfTheMap() {
        RandomOreLayout layout = new RandomOreLayout(7, 96, 64);

        int oreCells = 0;
        int totalCells = 0;
        for (int x = 0; x < 96; x++) {
            for (int y = 0; y < 64; y++) {
                totalCells++;
                if (layout.hasOre(x, y)) {
                    oreCells++;
                }
            }
        }

        assertTrue(oreCells > 0, "a rolled map must have at least some ore");
        assertTrue(oreCells < totalCells, "ore patches must not cover the entire map");
    }

    @Test
    void staysInBoundsEvenOnATinyMap() {
        RandomOreLayout layout = new RandomOreLayout(1, 6, 6);

        assertFalse(layout.oreAt(-1, -1).isPresent());
    }

    /**
     * A rolled patch must land ON the map whatever the map's size (N9, NEW_BUGS_PROGRESS.md). The
     * centre used to be rolled as {@code radius + nextInt(max(1, width - 2 * radius))}, which for a
     * map narrower than {@code 2 * radius} degenerates to exactly {@code radius} — a centre PAST the
     * right/bottom edge, leaving a patch that is clipped, off-centre, or (on the smallest maps)
     * entirely outside the grid, so the map comes out with no ore at all. Nothing crashed and
     * nothing was written out of bounds (the grid loop only ever visits real cells), but the roll
     * silently stopped meaning what it says.
     */
    @Test
    void everyRolledPatchLandsOnTheMapEvenWhenTheMapIsSmallerThanAPatch() {
        for (int size = 1; size <= 6; size++) {
            RandomOreLayout layout = new RandomOreLayout(3, size, size);

            boolean sawOre = false;
            for (int x = 0; x < size && !sawOre; x++) {
                for (int y = 0; y < size && !sawOre; y++) {
                    sawOre = layout.hasOre(x, y);
                }
            }
            assertTrue(sawOre, size + "x" + size + " map: patches must be shrunk to fit, not rolled off the grid");
        }
    }

    /** (X-02, DEV_TASKS.md) A rolled map has both obstacle kinds, and — same seed, same map — terrain repeats too. */
    @Test
    void rollsBothWaterAndRockAndRepeatsWithTheSameSeed() {
        RandomOreLayout a = new RandomOreLayout(7, 96, 64);
        RandomOreLayout b = new RandomOreLayout(7, 96, 64);

        boolean sawWater = false;
        boolean sawRock = false;
        for (int x = 0; x < 96; x++) {
            for (int y = 0; y < 64; y++) {
                Terrain terrain = a.terrainAt(x, y);
                sawWater |= terrain == Terrain.WATER;
                sawRock |= terrain == Terrain.ROCK;
                assertEquals(terrain, b.terrainAt(x, y), "same seed must roll the same terrain too, not just the same ore");
            }
        }
        assertTrue(sawWater, "a rolled map must have at least one water cell");
        assertTrue(sawRock, "a rolled map must have at least one rock cell");
    }

    /** (X-02, DEV_TASKS.md) Ore always wins — no cell reports both ore and impassable terrain, even after a random roll. */
    @Test
    void noOreCellIsEverImpassable() {
        RandomOreLayout layout = new RandomOreLayout(7, 96, 64);

        for (int x = 0; x < 96; x++) {
            for (int y = 0; y < 64; y++) {
                if (layout.hasOre(x, y)) {
                    assertTrue(layout.isPassable(x, y), "ore cell (" + x + "," + y + ") must never be impassable terrain");
                }
            }
        }
    }

    /** (D-04, DEV_TASKS.md) A fresh cell's reserve yields on every single call, no exceptions. */
    @Test
    void extractYieldsEveryCallWhileWithinRichness() {
        RandomOreLayout layout = new RandomOreLayout(7, 96, 64);
        int[] oreCell = findAnOreCell(layout);

        for (int i = 0; i < OreDepletion.RICHNESS; i++) {
            assertTrue(layout.extract(oreCell[0], oreCell[1]).isPresent(),
                    "call " + i + " should still be within the cell's undepleted reserve");
        }
    }

    /** (D-04, DEV_TASKS.md) Past the reserve, only every {@code TAIL_INTERVAL}th call still yields — the thin tail. */
    @Test
    void extractThrottlesToTheTailIntervalOnceDepleted() {
        RandomOreLayout layout = new RandomOreLayout(7, 96, 64);
        int[] oreCell = findAnOreCell(layout);

        for (int i = 0; i < OreDepletion.RICHNESS; i++) {
            layout.extract(oreCell[0], oreCell[1]); // spend the reserve
        }

        int yieldedCalls = 0;
        int tailCallsToCheck = OreDepletion.TAIL_INTERVAL * 5;
        for (int i = 0; i < tailCallsToCheck; i++) {
            if (layout.extract(oreCell[0], oreCell[1]).isPresent()) {
                yieldedCalls++;
            }
        }

        assertEquals(5, yieldedCalls,
                "exactly 1 in " + OreDepletion.TAIL_INTERVAL + " calls should yield once depleted, never zero");
    }

    /** (D-04, DEV_TASKS.md) {@code extract} must never change what {@code oreAt} reports — only rendering reads it. */
    @Test
    void extractNeverChangesWhatOreAtReports() {
        RandomOreLayout layout = new RandomOreLayout(7, 96, 64);
        int[] oreCell = findAnOreCell(layout);
        var before = layout.oreAt(oreCell[0], oreCell[1]);

        for (int i = 0; i < OreDepletion.RICHNESS + OreDepletion.TAIL_INTERVAL * 3; i++) {
            layout.extract(oreCell[0], oreCell[1]);
        }

        assertEquals(before, layout.oreAt(oreCell[0], oreCell[1]),
                "oreAt must stay a pure report of what's there, unaffected by however many times extract was called");
    }

    /** (D-05, DEV_TASKS.md) A rolled map has minable coal too, not just iron/bronze. */
    @Test
    void rollsMinableCoal() {
        RandomOreLayout layout = new RandomOreLayout(7, 96, 64);

        boolean sawCoal = false;
        for (int x = 0; x < 96 && !sawCoal; x++) {
            for (int y = 0; y < 64 && !sawCoal; y++) {
                if (layout.oreAt(x, y).equals(Optional.of(VanillaItems.COAL))) {
                    sawCoal = true;
                    assertEquals(Optional.of(VanillaItems.COAL), layout.extract(x, y));
                }
            }
        }
        assertTrue(sawCoal, "a rolled map must have at least one coal cell");
    }

    private static int[] findAnOreCell(RandomOreLayout layout) {
        for (int x = 0; x < 96; x++) {
            for (int y = 0; y < 64; y++) {
                if (layout.hasOre(x, y)) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("fixture needs at least one ore cell — seed produced none");
    }
}
