package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
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
     * A permanently blocked assigned side must not wedge the item forever when the OTHER side is
     * open — the splitter falls back to it the same tick instead of stalling (found in review: the
     * old "wait, never reroute" behavior stalled BOTH outputs the moment either one jammed, which
     * defeats a balancer far more than a temporarily uneven split does).
     */
    @Test
    void blockedAssignedSideFallsBackToTheOpenSecondarySideRatherThanStallingForever() {
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.RIGHT);
        // Forward (RIGHT) neighbor deliberately left unbuilt — nothing there to accept anything.
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side); // secondary (rotate(RIGHT) = DOWN) neighbor — open and willing

        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();
        assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));

        world.tick();

        assertEquals(1, side.count(), "must have fallen back to the open secondary side instead of stalling");
        assertEquals(Optional.empty(), splitter.heldItem());
    }

    /**
     * A fallback delivery must NOT flip {@link Splitter}'s round-robin bookkeeping — the assigned
     * side stays "next" so it gets first refusal again once it recovers, instead of the fallback
     * itself silently becoming the new normal turn order.
     */
    @Test
    void fallbackDeliveryDoesNotStealTheAssignedSidesNextTurn() {
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.RIGHT);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side); // secondary — open the whole time

        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();
        assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));
        world.tick(); // forward still unbuilt -> falls back to secondary; nextIsForward must stay true

        world.place(BuildingType.CHEST, 2, 1); // forward now exists and is open
        Chest forward = (Chest) world.peek(2, 1).orElseThrow();
        assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));
        world.tick();

        assertEquals(1, forward.count(), "the assigned (forward) side must still be tried first, not secondary again");
        assertEquals(1, side.count(), "unchanged from the earlier fallback delivery");
    }

    /**
     * The scenario the fallback exists for: one side of a splitter jams permanently (nothing ever
     * drains it) while the other stays completely open. Before the fix, EVERY future item stalled
     * on the jammed side forever, because {@code nextIsForward} only flips on success — starving a
     * perfectly healthy output because its unrelated sibling got stuck.
     */
    @Test
    void permanentlyBlockedSideNoLongerStarvesTheOpenSideForFutureItems() {
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.RIGHT);
        world.place(BuildingType.CHEST, 2, 1); // forward — about to be demolished mid-run
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side); // secondary — stays open for the whole run

        Splitter splitter = (Splitter) world.peek(1, 1).orElseThrow();
        for (int i = 0; i < 4; i++) {
            assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));
            world.tick();
        }
        Chest forward = (Chest) world.peek(2, 1).orElseThrow();
        assertEquals(2, forward.count());
        assertEquals(2, side.count());

        world.removeBuilding(2, 1); // forward now permanently unreachable
        for (int i = 0; i < 10; i++) {
            assertTrue(splitter.accept(world, VanillaItems.IRON_ORE));
            world.tick();
        }

        assertEquals(12, side.count(), "the open side must keep receiving every future item, not just the ones already in flight before the jam");
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

        SplitterState state = (SplitterState) original.state();
        Splitter reloaded = new Splitter(state.facing(), state.held(), state.nextIsForward());

        assertEquals(Optional.of(Direction.UP), reloaded.outputDirection());
        assertEquals(Optional.of(VanillaItems.GEAR), reloaded.heldItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesEmptyCargo() {
        Splitter original = new Splitter(Direction.LEFT);

        SplitterState state = (SplitterState) original.state();
        Splitter reloaded = new Splitter(state.facing(), state.held(), state.nextIsForward());

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

        SplitterState state = (SplitterState) splitter.state();

        assertEquals(false, state.nextIsForward(), "after one forward delivery, the NEXT item must be assigned to the secondary side");
    }
}
