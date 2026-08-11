package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ActionHistory}: undo/redo bookkeeping — the part every {@link PlayerAction} relies on. */
class ActionHistoryTest {

    @Test
    void undoRemovesExactlyWhatWasPlaced() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();

        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1));
        assertTrue(world.peek(1, 1).isPresent());

        history.undo(world);
        assertFalse(world.peek(1, 1).isPresent());
    }

    @Test
    void redoReappliesAfterUndo() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();

        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1));
        history.undo(world);
        history.redo(world);

        assertTrue(world.peek(1, 1).isPresent());
    }

    @Test
    void performingANewActionClearsTheRedoStack() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();

        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1));
        history.undo(world);
        history.perform(world, new PlaceAction(BuildingType.CHEST, 2, 2));

        history.redo(world); // nothing left to redo — the (1,1) chest's redo was discarded
        assertFalse(world.peek(1, 1).isPresent());
        assertTrue(world.peek(2, 2).isPresent());
    }

    @Test
    void undoAndRedoOnEmptyHistoryDoNothing() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();

        history.undo(world);
        history.redo(world);
        // No exception, no state change — nothing to assert beyond "it didn't blow up".
    }

    @Test
    void failedApplyIsNeverRememberedForUndo() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();
        world.placeChest(1, 1);

        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1)); // cell occupied — fails

        history.undo(world); // must NOT remove the original chest
        assertTrue(world.peek(1, 1).isPresent());
    }

    @Test
    void compositeActionUndoesEveryPlacementAtOnce() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();

        history.perform(world, new CompositeAction(List.of(
                new PlaceAction(BuildingType.BELT, 0, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, 1, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, 2, 0, Direction.RIGHT))));

        assertTrue(world.peek(0, 0).isPresent());
        assertTrue(world.peek(1, 0).isPresent());
        assertTrue(world.peek(2, 0).isPresent());

        history.undo(world);

        assertFalse(world.peek(0, 0).isPresent());
        assertFalse(world.peek(1, 0).isPresent());
        assertFalse(world.peek(2, 0).isPresent());
    }

    @Test
    void undoOfADragMustNotRemoveBuildingsItNeverPlaced() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();
        world.placeChest(1, 0);

        history.perform(world, new CompositeAction(List.of(
                new PlaceAction(BuildingType.BELT, 0, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, 1, 0, Direction.RIGHT), // cell occupied — fails
                new PlaceAction(BuildingType.BELT, 2, 0, Direction.RIGHT))));

        history.undo(world);

        assertFalse(world.peek(0, 0).isPresent());
        assertFalse(world.peek(2, 0).isPresent());
        assertTrue(world.peek(1, 0)
                .map(b -> b.prototypeId().equals(VanillaBuildings.idFor(BuildingType.CHEST)))
                .orElse(false));
    }

    /**
     * Uses {@link RemoveAction} on chests placed directly (free, bypassing {@link PlaceAction}'s
     * cost), not {@code PlaceAction} itself: this test is about {@code ActionHistory}'s depth cap,
     * a mechanical property orthogonal to D-03's building costs, and 210 real placements would cost
     * far more {@code IRON_PLATE} than any reasonable starting inventory — inflating the starting
     * stock just to keep a stress test affordable would be tuning gameplay balance backwards from a
     * test, not the other way around. {@code RemoveAction} needs no funds to apply (it only ever
     * refunds), so it exercises the same eviction mechanics without that constraint.
     */
    @Test
    void historyDoesNotGrowBeyondItsDepthLimit() {
        int maxDepth = 200; // must match ActionHistory.MAX_DEPTH
        int removals = maxDepth + 10;
        World world = new World(removals, 1);
        for (int x = 0; x < removals; x++) {
            world.placeChest(x, 0);
        }
        ActionHistory history = new ActionHistory();

        for (int x = 0; x < removals; x++) {
            history.perform(world, new RemoveAction(x, 0));
        }

        for (int i = 0; i < removals; i++) {
            history.undo(world);
        }

        // The earliest removals fell out of the (capped) history — their undo was discarded, so
        // those chests must still be gone.
        for (int x = 0; x < removals - maxDepth; x++) {
            assertFalse(world.peek(x, 0).isPresent(),
                    "chest at " + x + " should have stayed removed — its undo entry was evicted");
        }
        // The most recent MAX_DEPTH removals are still within the cap and must have been undone
        // (their chest restored).
        for (int x = removals - maxDepth; x < removals; x++) {
            assertTrue(world.peek(x, 0).isPresent(), "chest at " + x + " should have been restored");
        }
    }

    /**
     * (D-03, DEV_TASKS.md) The risk the card itself calls out: a drag builds several tiles as one
     * {@link CompositeAction}, and running out of resources partway through must charge for exactly
     * what got placed — not the whole drag, not nothing — and undo must refund exactly that much
     * back, not more.
     */
    @Test
    void compositeActionOnlyChargesForWhatActuallyGotPlaced() {
        World world = new World(50, 1);
        ActionHistory history = new ActionHistory();
        int startingPlates = world.inventory().amount(VanillaItems.IRON_PLATE);

        // Spend down to exactly 2 IRON_PLATE left (BELT costs 1 each), leaving no room to guess a
        // hardcoded starting-stock number here.
        for (int x = 0; x < startingPlates - 2; x++) {
            history.perform(world, new PlaceAction(BuildingType.BELT, x, 0, Direction.RIGHT));
        }
        assertEquals(2, world.inventory().amount(VanillaItems.IRON_PLATE));

        int dragStart = startingPlates - 2;
        history.perform(world, new CompositeAction(List.of(
                new PlaceAction(BuildingType.BELT, dragStart, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, dragStart + 1, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, dragStart + 2, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, dragStart + 3, 0, Direction.RIGHT),
                new PlaceAction(BuildingType.BELT, dragStart + 4, 0, Direction.RIGHT))));

        assertTrue(world.peek(dragStart, 0).isPresent(), "affordable with the last 2 plates");
        assertTrue(world.peek(dragStart + 1, 0).isPresent(), "affordable with the last 2 plates");
        assertFalse(world.peek(dragStart + 2, 0).isPresent(), "out of plates — must not have been placed");
        assertFalse(world.peek(dragStart + 3, 0).isPresent());
        assertFalse(world.peek(dragStart + 4, 0).isPresent());
        assertEquals(0, world.inventory().amount(VanillaItems.IRON_PLATE), "exactly the 2 affordable belts must have been charged");

        history.undo(world);

        assertFalse(world.peek(dragStart, 0).isPresent());
        assertFalse(world.peek(dragStart + 1, 0).isPresent());
        assertEquals(2, world.inventory().amount(VanillaItems.IRON_PLATE),
                "undo must refund exactly the 2 belts that actually got placed, not the whole drag");
    }

    /**
     * A redo that FAILS must not land in the undo stack (N1, NEW_BUGS_PROGRESS.md). {@code redo}
     * used to ignore {@code apply}'s return value, so a redo blocked by a now-occupied cell was
     * still remembered as done — and the next {@code undo} then demolished (and refunded) a
     * building this action never placed.
     */
    @Test
    void failedRedoIsNeverRememberedForUndo() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();

        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1));
        history.undo(world);
        int platesAfterUndo = world.inventory().amount(VanillaItems.IRON_PLATE);

        // Built directly, outside the history — the cell the redo wants is no longer free.
        world.placeBelt(1, 1, Direction.RIGHT);

        history.redo(world); // must fail: PlaceAction charges, can't place, refunds, returns false
        assertTrue(world.peek(1, 1)
                .map(b -> b.prototypeId().equals(VanillaBuildings.idFor(BuildingType.BELT)))
                .orElse(false), "the failed redo must not have replaced the belt");

        history.undo(world);

        assertTrue(world.peek(1, 1)
                .map(b -> b.prototypeId().equals(VanillaBuildings.idFor(BuildingType.BELT)))
                .orElse(false), "undo must not demolish a building the failed redo never placed");
        assertEquals(platesAfterUndo, world.inventory().amount(VanillaItems.IRON_PLATE),
                "a failed redo followed by undo must not refund a cost that was never spent");
    }

    @Test
    void undoOfARemoveMustNotOverwriteWhateverWasBuiltThere() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();
        world.placeChest(1, 1);

        history.perform(world, new RemoveAction(1, 1));
        assertFalse(world.peek(1, 1).isPresent());

        // Built directly, outside the undo history — a player action taken after the demolition.
        world.placeBelt(1, 1, Direction.RIGHT);

        history.undo(world); // undoes the RemoveAction; the cell is occupied by the new belt

        assertTrue(world.peek(1, 1)
                .map(b -> b.prototypeId().equals(VanillaBuildings.idFor(BuildingType.BELT)))
                .orElse(false), "the newer belt must not be overwritten by the restored chest");
    }
}
