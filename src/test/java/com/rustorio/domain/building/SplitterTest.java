package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Splitter} (X-01, DEV_TASKS.md): now a real round-robin balancer — alternates which of its
 * two outputs gets the next item, never by item identity (that's {@link Filter}'s job now, not
 * this class's). The old {@code SortRule}-based single-hardcoded-rule filter this class used to be
 * is gone entirely.
 */
class SplitterTest {

    @Test
    void alternatesBetweenForwardAndSecondaryOutputRegardlessOfItemIdentity() {
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.RIGHT);
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT); // faces off the map — a passive receiver, see ChestTest's own note on this trick
        world.restoreBuilding(1, 2, side);

        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();

        // Same item every time — a SortRule-based filter would send ALL of these the same way;
        // a round-robin balancer must still alternate.
        for (int i = 0; i < 4; i++) {
            assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));
            world.tick();
        }

        assertEquals(2, forward.count(), "half of 4 items must have gone forward");
        assertEquals(2, side.count(), "and the other half to the rotated side");
    }

    /**
     * Honesty of the split: if the side a round-robin splitter is CURRENTLY assigned to is
     * blocked, it must wait for that side to clear, not silently dump the item on the other
     * (open) side — the same "hold until delivered" discipline every other producer follows, and
     * the only thing that keeps a 50/50 split meaningful under backpressure.
     */
    @Test
    void blockedForwardSideMakesTheSplitterWaitRatherThanReroutingToTheOpenSecondarySide() {
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.RIGHT);
        // Forward (RIGHT) neighbor deliberately left unbuilt — nothing there to accept anything.
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side); // secondary (rotate(RIGHT) = DOWN) neighbor — open and willing

        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();
        assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));

        for (int i = 0; i < 5; i++) {
            world.tick();
        }

        assertEquals(0, side.count(), "must NOT have opportunistically rerouted to the open side");
        assertEquals(Optional.of(VanillaItems.IRON_ORE), splitter.heldItem(), "must still be holding, waiting for its assigned side");
    }

    /**
     * One tile per tick ACROSS a chain (N2, NEW_BUGS_PROGRESS.md) — see {@code InserterTest}'s
     * twin of this test for the mechanism. A chain of leftward splitters used to relay an item
     * through every one of them within a single tick (unbounded transport speed).
     */
    @Test
    void cargoHandedOverMidTickWaitsForTheNextTickInsteadOfCrossingTheWholeChain() {
        World world = new World(20, 20);
        world.placeSplitter(11, 10, Direction.LEFT);
        world.placeSplitter(10, 10, Direction.LEFT);
        Chest target = new Chest(Direction.UP); // faces away — a passive receiver
        world.restoreBuilding(9, 10, target);

        Splitter upstream = (Splitter) world.peek(11, 10).orElseThrow();
        Splitter downstream = (Splitter) world.peek(10, 10).orElseThrow();
        assertTrue(upstream.accept(world, VanillaItems.IRON_ORE));

        world.tick();

        assertEquals(0, target.count(), "the item must not cross two splitters in one tick");
        assertEquals(Optional.of(VanillaItems.IRON_ORE), downstream.heldItem(), "it settles on the middle splitter first");

        world.tick();

        assertEquals(1, target.count(), "and moves on the NEXT tick");
    }

    @Test
    void rotatingRotatesBothOutputsTogether() {
        // facing=DOWN: forward now points DOWN, and the rotated-clockwise side from DOWN is LEFT
        // (see Direction.rotate: RIGHT->DOWN->LEFT->UP->RIGHT).
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.DOWN);
        Chest forward = new Chest();
        world.restoreBuilding(1, 2, forward); // (x, y+1) — DOWN

        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();
        assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));

        world.tick();
        assertEquals(1, forward.count(), "first item goes forward (round-robin starts forward)");
    }

    @Test
    void saveAndLoadRoundTripPreservesFacingCargoAndRoundRobinState() {
        Splitter original = new Splitter(Direction.UP);
        original.accept(null, VanillaItems.GEAR);

        BuildingMemento.SplitterState memento = (BuildingMemento.SplitterState) original.memento();
        Splitter reloaded = new Splitter(memento.facing(), memento.held(), memento.nextIsForward());

        assertEquals(Optional.of(Direction.UP), reloaded.outputDirection());
        assertEquals(Optional.of(VanillaItems.GEAR), reloaded.heldItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesEmptyCargo() {
        Splitter original = new Splitter(Direction.LEFT);

        BuildingMemento.SplitterState memento = (BuildingMemento.SplitterState) original.memento();
        Splitter reloaded = new Splitter(memento.facing(), memento.held(), memento.nextIsForward());

        assertEquals(Optional.of(Direction.LEFT), reloaded.outputDirection());
        assertEquals(Optional.empty(), reloaded.heldItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesWhichSideIsNext() {
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.RIGHT);
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();
        splitter.accept(world, VanillaItems.IRON_ORE);
        world.tick(); // delivers forward, flips nextIsForward to false

        BuildingMemento.SplitterState memento = (BuildingMemento.SplitterState) splitter.memento();

        assertEquals(false, memento.nextIsForward(), "after one forward delivery, the NEXT item must be assigned to the secondary side");
    }
}
