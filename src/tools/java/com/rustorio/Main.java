package com.rustorio;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;

/**
 * Headless entry point: builds a small iron chain, ticks it, and prints what happens — no
 * window, no libGDX. Enough to see the simulation work on its own; {@code com.graphics.Main}
 * is the windowed, playable game built on the same {@link World}.
 */
public final class Main {

    private static final int TICKS = 120;
    private static final int REPORT_EVERY = 20;

    public static void main(String[] args) {
        World world = new World(12, 8);
        // A miner needs ore under it — (6, 5) sits at the center of the standard map's first iron
        // patch, so it's guaranteed to have ore regardless of how the patch layout evolves.
        world.placeMiner(6, 5);
        world.placeBelt(7, 5, Direction.RIGHT);
        world.placeFurnace(8, 5, Direction.RIGHT);
        world.placeBelt(9, 5, Direction.RIGHT);
        world.placeChest(10, 5);

        System.out.println("""
                Rustorio — headless run
                Miner(6,5) -> Belt(7,5) -> Furnace(8,5) -> Belt(9,5) -> Chest(10,5)
                """);

        for (int tick = 1; tick <= TICKS; tick++) {
            world.tick();
            if (tick % REPORT_EVERY == 0) {
                System.out.printf("tick %3d: ore mined %2d, plates smelted %2d%n",
                        tick, world.stats().total(Item.IRON_ORE), world.stats().total(Item.IRON_PLATE));
            }
        }
    }
}
