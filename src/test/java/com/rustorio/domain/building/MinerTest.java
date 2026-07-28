package com.rustorio.domain.building;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (D-01, DEV_TASKS.md) A miner must deliver only to the single cell ahead of its own direction,
 * never broadcast to whichever of the four neighbors happens to accept — this is the fix for §2.1
 * of the design audit, where a miner worked next to ANY accepting neighbor regardless of facing,
 * making belts and layout optional.
 */
class MinerTest {

    @Test
    void minerDoesNotLeakOreToANonForwardNeighbor() {
        World world = new World(10, 10);
        assertTrue(world.place(BuildingType.MINER, 6, 5, Direction.RIGHT),
                "(6,5) is the center of the standard map's first iron patch");
        world.placeChest(6, 6); // DOWN neighbor — not the miner's forward (RIGHT) cell
        // (7,5), the actual forward cell, is deliberately left empty.

        Building miner = world.peek(6, 5).orElseThrow();
        for (int i = 0; i < 20; i++) {
            world.tick();
        }

        Chest sideChest = (Chest) world.peek(6, 6).orElseThrow();
        assertEquals(0, sideChest.count(),
                "ore must never reach a neighbor that isn't the miner's forward cell");
        assertTrue(miner.heldItem().isPresent(),
                "with its forward cell empty, the miner must keep holding what it mined, not leak it sideways");
        assertEquals(BuildingStatus.OUTPUT_FULL, miner.appearance().status(),
                "(F-01, DEV_TASKS.md) holding a finished item with nowhere to deliver it is OUTPUT_FULL");
    }

    @Test
    void minerDeliversToItsForwardNeighbor() {
        World world = new World(10, 10);
        assertTrue(world.place(BuildingType.MINER, 6, 5, Direction.RIGHT));
        world.placeChest(7, 5); // forward (RIGHT) neighbor

        for (int i = 0; i < 20; i++) {
            world.tick();
        }

        Chest forwardChest = (Chest) world.peek(7, 5).orElseThrow();
        assertTrue(forwardChest.count() > 0, "ore delivered straight ahead must actually arrive");
        Building miner = world.peek(6, 5).orElseThrow();
        assertEquals(BuildingStatus.WORKING, miner.appearance().status(),
                "(F-01, DEV_TASKS.md) a chest with room to spare must let the miner keep delivering, not stall it");
    }

    /**
     * (N14, NEW_BUGS_PROGRESS.md — owner decision) {@code NO_ORE} means "there is no ore under this
     * miner", never "this cycle happened to come up empty". A depleted cell still yields on 1 call in
     * {@code OreDepletion.TAIL_INTERVAL} (D-04), so the other nine attempts used to flip the status
     * to {@code NO_ORE} and back — a miner that is slowly but genuinely working rendered as if it
     * were standing on bare ground. The alternative considered and rejected was a third status
     * ({@code DEPLETED}): the miner IS working, just slowly, and that is what {@code WORKING} says.
     */
    @Test
    void minerOnADepletedVeinKeepsReportingWorkingInsteadOfFlickeringToNoOre() {
        World world = new World(10, 10);
        PatchOreLayout layout = PatchOreLayout.standard();
        Miner miner = new Miner(layout, Direction.RIGHT);
        world.restoreBuilding(6, 5, miner); // centre of the standard map's first iron patch
        world.placeChest(7, 5); // somewhere for the ore to go, so OUTPUT_FULL never enters the picture

        // Spend the cell's whole reserve by hand, leaving it in its thin tail. Both numbers mirror
        // OreDepletion's own (package-private) RICHNESS/TAIL_INTERVAL — same "mirrors a private
        // constant" note ChestTest's CAPACITY already carries.
        int richness = 6000;
        int tailInterval = 10;
        for (int i = 0; i < richness; i++) {
            layout.extract(6, 5);
        }

        // Long enough to cover many mining cycles, i.e. many non-yielding attempts.
        for (int i = 0; i < tailInterval * 3; i++) {
            world.tick();
            assertEquals(BuildingStatus.WORKING, miner.appearance().status(),
                    "tick " + i + ": the vein still has ore, the miner is just slow — that is not NO_ORE");
        }
    }

    /** (F-01, DEV_TASKS.md) A miner sitting on a cell with no ore under it must report NO_ORE, not just idle silently. */
    @Test
    void minerReportsNoOreStatusWhenNoOreUnderIt() {
        World world = new World(10, 10);
        // (0,0) is far outside every ore patch's radius in the standard map — see PatchOreLayout.
        Miner miner = new Miner(PatchOreLayout.standard(), Direction.RIGHT);
        world.restoreBuilding(0, 0, miner);

        for (int i = 0; i < 3; i++) { // MINE_TIME ticks to reach the actual extraction attempt
            world.tick();
        }

        assertEquals(BuildingStatus.NO_ORE, miner.appearance().status());
    }
}
