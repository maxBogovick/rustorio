package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (D-01, DEV_TASKS.md) {@link RotateAction}: turning an already-placed building without demolishing
 * it — the other half of the D-01 pairing (directed delivery makes a wrong facing a trap; this is
 * the way out of that trap that doesn't cost the building's resources back and forth).
 */
class RotateActionTest {

    @Test
    void rotatingAFurnacePreservesItsBufferedInput() {
        World world = new World(4, 4);
        assertTrue(world.placeFurnace(1, 1, Direction.RIGHT));
        Furnace furnace = (Furnace) world.peek(1, 1).orElseThrow();
        furnace.accept(world, Item.IRON_ORE);

        assertTrue(new RotateAction(1, 1).apply(world));

        Furnace rotated = (Furnace) world.peek(1, 1).orElseThrow();
        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(1, rotated.oreBuffer(), "rotating must not discard whatever the furnace was already cooking");
    }

    @Test
    void rotatingABeltInTheMiddleOfASegmentReattachesItInTheNewDirection() {
        World world = new World(5, 5);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(1, 0, Direction.RIGHT)); // the one we rotate — middle of the segment
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));

        assertTrue(new RotateAction(1, 0).apply(world));

        Belt rotated = (Belt) world.peek(1, 0).orElseThrow();
        assertEquals(Direction.DOWN, rotated.direction());
        // The segment split around the rotated tile must still tick without throwing.
        world.tick();
    }

    @Test
    void undoRestoresTheOriginalDirection() {
        World world = new World(4, 4);
        world.placeFurnace(1, 1, Direction.RIGHT);
        RotateAction action = new RotateAction(1, 1);

        assertTrue(action.apply(world));
        action.undo(world);

        Building restored = world.peek(1, 1).orElseThrow();
        assertEquals(Optional.of(Direction.RIGHT), restored.outputDirection());
    }

    @Test
    void redoReappliesTheRotationThroughActionHistory() {
        World world = new World(4, 4);
        world.placeFurnace(1, 1, Direction.RIGHT);
        ActionHistory history = new ActionHistory();

        history.perform(world, new RotateAction(1, 1));
        history.undo(world);
        history.redo(world);

        Building building = world.peek(1, 1).orElseThrow();
        assertEquals(Optional.of(Direction.DOWN), building.outputDirection());
    }

    @Test
    void rotatingABuildingWithNoDirectionIsRefusedAndNotRecorded() {
        World world = new World(4, 4);
        world.placeChest(1, 1);
        ActionHistory history = new ActionHistory();

        history.perform(world, new RotateAction(1, 1)); // apply() returns false — nothing to undo

        history.undo(world); // must be a no-op: the failed rotation was never remembered
        assertTrue(world.peek(1, 1).isPresent(), "the chest itself must still be standing, untouched");
    }

    @Test
    void rotatingAnEmptyCellDoesNothing() {
        World world = new World(4, 4);
        assertFalse(new RotateAction(1, 1).apply(world));
    }

    @Test
    void rotatingASpeedUpgradedBuildingDelegatesThroughTheWrapperAndKeepsTheUpgrade() {
        World world = new World(4, 4);
        world.placeFurnace(1, 1, Direction.RIGHT);
        new UpgradeSpeedAction(1, 1).apply(world);

        assertTrue(new RotateAction(1, 1).apply(world));

        Building rotated = world.peek(1, 1).orElseThrow();
        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(1, rotated.speedLevel(), "rotating must not strip the SpeedModule wrapper");
    }

    /**
     * (X-03, DEV_TASKS.md) {@code ASSEMBLER} occupies 2x2 cells — clicking any one of them (not
     * only its anchor) must rotate the SAME building, and the footprint must stay exactly where it
     * was, not shift to wherever the click happened to land (see {@link RotateAction}'s own
     * javadoc on {@code anchorX}/{@code anchorY}).
     */
    @Test
    void rotatingAMultiCellBuildingFromANonAnchorCellRotatesTheWholeThingInPlace() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        assertTrue(new RotateAction(3, 3).apply(world), "clicked the far corner, not the anchor");

        Building rotated = world.peek(2, 2).orElseThrow();
        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertTrue(world.peek(2, 2).orElseThrow() == world.peek(3, 3).orElseThrow(),
                "still the same one building, still spanning all four original cells");
    }

    /**
     * Undo must act on the state it left behind, not on whatever occupies the cell later (N8,
     * NEW_BUGS_PROGRESS.md) — the same rule {@link RemoveAction#undo} already follows. A rotation's
     * undo used to demolish the current occupant unconditionally and drop the old building on top
     * of it; the cell can genuinely change hands in between, since a failed undo (an unaffordable
     * {@code RemoveAction}, say) leaves everything ABOVE it in the stack still reachable.
     */
    @Test
    void undoDoesNothingIfSomethingElseNowStandsOnTheCell() {
        World world = new World(10, 10);
        world.placeBelt(1, 1, Direction.RIGHT);
        RotateAction action = new RotateAction(1, 1);
        assertTrue(action.apply(world));

        // The rotated belt is taken off the map and a different building goes up in its place.
        world.removeBuilding(1, 1);
        world.placeChest(1, 1);

        action.undo(world);

        assertEquals(BuildingType.CHEST, world.peek(1, 1).orElseThrow().type(),
                "the newer chest must not be replaced by the un-rotated belt");
    }

    @Test
    void undoOfARotationTriggeredFromANonAnchorCellRestoresTheOriginalFacingAtTheAnchor() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);
        RotateAction action = new RotateAction(3, 2); // top-right cell, still not the anchor

        action.apply(world);
        action.undo(world);

        Building restored = world.peek(2, 2).orElseThrow();
        assertEquals(Optional.of(Direction.RIGHT), restored.outputDirection());
        assertTrue(world.isFree(4, 2), "undo must not have left the building shifted onto neighboring cells");
    }
}
