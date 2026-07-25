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
}
