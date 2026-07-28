package com.graphics.screen;

import com.graphics.GfxConfig;
import com.rustorio.domain.Item;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DevScene}: every recipe chain it builds must actually RUN, not just place without
 * throwing — a coordinate collision between two chains (two chests planned for the same cell)
 * would silently fail one placement and merge two supply chests into one, which compiles and
 * doesn't crash, but quietly breaks the "every recipe genuinely produces" promise. This runs the
 * exact same {@link World#tick()} loop the real game does and checks every chain's own output
 * chest actually accumulated the item it's supposed to make.
 */
class DevSceneTest {

    // Generous — even the slowest single chain (CHASSIS, 15 ticks/batch, fed from its own
    // pre-stocked supply chests, not from any other chain here) finishes several batches well
    // within this many ticks.
    private static final int TICKS = 2000;

    @Test
    void everyRecipeChainActuallyProducesItsOutput() {
        World world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H);
        DevScene.build(world);

        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        assertOutput(world, 10, 5, Item.IRON_PLATE);
        assertOutput(world, 10, 8, Item.BRONZE_PLATE);
        assertOutput(world, 10, 11, Item.GEAR);
        assertOutput(world, 10, 14, Item.MECHANISM);
        assertOutput(world, 10, 17, Item.ENGINE);
        assertOutput(world, 10, 20, Item.CHASSIS);
        assertOutput(world, 10, 23, Item.ALLOY_PLATE);
        assertOutput(world, 10, 26, Item.ALLOY_GEAR);
    }

    /** The deliberately broken miner must actually stay broken — a live demonstration of NO_ORE, not an accident. */
    @Test
    void thePlantedBrokenMinerNeverMinesAnything() {
        World world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H);
        DevScene.build(world);

        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        assertTrue(world.peek(1, 1).orElseThrow().heldItem().isEmpty(),
                "the stranded miner sits off every ore patch on purpose — it must never actually mine");
    }

    private static void assertOutput(World world, int x, int y, Item expected) {
        Chest output = (Chest) world.peek(x, y).orElseThrow();
        assertTrue(output.amount(expected) > 0,
                "(" + x + "," + y + ")'s output chest must have accumulated at least one " + expected
                        + " after " + TICKS + " ticks");
    }
}
