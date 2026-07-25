package com.rustorio.domain;

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
}
