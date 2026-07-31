package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Inserter} (X-01, DEV_TASKS.md): restores direct transfer between two adjacent machines
 * that D-01 took away (delivery addressed to one cell, not broadcast to any accepting neighbor) —
 * see the class's own javadoc for why this is mechanically a single {@link Belt} tile.
 */
class InserterTest {

    @Test
    void receivesOneItemAndDeliversItForward() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Inserter(Direction.RIGHT));
        Chest target = new Chest();
        world.restoreBuilding(2, 1, target);

        Inserter inserter = (Inserter) world.peek(1, 1).orElseThrow();
        assertTrue(inserter.accept(world, VanillaItems.IRON_PLATE));

        world.tick();

        assertEquals(1, target.count());
        assertEquals(Optional.empty(), inserter.heldItem());
    }

    @Test
    void refusesASecondItemWhileStillHoldingTheFirst() {
        Inserter inserter = new Inserter(Direction.RIGHT);
        assertTrue(inserter.accept(null, VanillaItems.IRON_PLATE));

        assertFalse(inserter.accept(null, VanillaItems.GEAR));
    }

    @Test
    void holdsWhenTheCellAheadCannotAcceptYet() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Inserter(Direction.RIGHT));
        // (2,1) deliberately left empty — nothing there to accept anything.

        Inserter inserter = (Inserter) world.peek(1, 1).orElseThrow();
        assertTrue(inserter.accept(world, VanillaItems.IRON_PLATE));

        world.tick();

        assertEquals(Optional.of(VanillaItems.IRON_PLATE), inserter.heldItem(), "must keep holding, not drop the item");
    }

    /**
     * One tile per tick ACROSS a chain, not just within one tile (N2, NEW_BUGS_PROGRESS.md). Two
     * leftward inserters tick in the descending pass, high x first, so the upstream one hands its
     * cargo to the downstream one before the downstream one has ticked — without an arrival mark
     * (the same one {@link Belt} already carries) the item crossed both cells in a single tick.
     */
    @Test
    void cargoHandedOverMidTickWaitsForTheNextTickInsteadOfCrossingTheWholeChain() {
        World world = new World(20, 20);
        world.restoreBuilding(11, 10, new Inserter(Direction.LEFT));
        world.restoreBuilding(10, 10, new Inserter(Direction.LEFT));
        Chest target = new Chest(Direction.UP); // faces away — a passive receiver
        world.restoreBuilding(9, 10, target);

        Inserter upstream = (Inserter) world.peek(11, 10).orElseThrow();
        Inserter downstream = (Inserter) world.peek(10, 10).orElseThrow();
        assertTrue(upstream.accept(world, VanillaItems.IRON_PLATE));

        world.tick();

        assertEquals(0, target.count(), "the item must not cross two cells in one tick");
        assertEquals(Optional.of(VanillaItems.IRON_PLATE), downstream.heldItem(), "it settles on the middle cell first");

        world.tick();

        assertEquals(1, target.count(), "and moves on the NEXT tick");
    }

    @Test
    void rotatingChangesDirectionButKeepsCargo() {
        Inserter inserter = new Inserter(Direction.RIGHT);
        inserter.accept(null, VanillaItems.GEAR);

        Inserter rotated = (Inserter) inserter.rotatedClockwise().orElseThrow();

        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(Optional.of(VanillaItems.GEAR), rotated.heldItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesDirectionAndCargo() {
        Inserter original = new Inserter(Direction.LEFT);
        original.accept(null, VanillaItems.CHASSIS);

        InserterState state = (InserterState) original.state();
        Inserter reloaded = new Inserter(state.direction(), state.held());

        assertEquals(Optional.of(Direction.LEFT), reloaded.outputDirection());
        assertEquals(Optional.of(VanillaItems.CHASSIS), reloaded.heldItem());
    }
}
