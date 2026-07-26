package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.world.World;
import java.util.List;
import org.junit.jupiter.api.Test;

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
                .map(b -> b.type() == BuildingType.CHEST)
                .orElse(false));
    }

    @Test
    void historyDoesNotGrowBeyondItsDepthLimit() {
        int maxDepth = 200; // must match ActionHistory.MAX_DEPTH
        int placements = maxDepth + 10;
        World world = new World(placements, 1);
        ActionHistory history = new ActionHistory();

        for (int x = 0; x < placements; x++) {
            history.perform(world, new PlaceAction(BuildingType.CHEST, x, 0));
        }

        for (int i = 0; i < placements; i++) {
            history.undo(world);
        }

        // The earliest placements fell out of the (capped) history — their undo was discarded, so
        // they must still be standing.
        for (int x = 0; x < placements - maxDepth; x++) {
            assertTrue(world.peek(x, 0).isPresent(),
                    "chest at " + x + " should have survived — its undo entry was evicted");
        }
        // The most recent MAX_DEPTH placements are still within the cap and must have been undone.
        for (int x = placements - maxDepth; x < placements; x++) {
            assertFalse(world.peek(x, 0).isPresent(), "chest at " + x + " should have been undone");
        }
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
                .map(b -> b.type() == BuildingType.BELT)
                .orElse(false), "the newer belt must not be overwritten by the restored chest");
    }
}
