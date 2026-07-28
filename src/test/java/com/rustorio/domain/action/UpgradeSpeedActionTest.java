package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UpgradeSpeedAction}: wrap/unwrap a {@link com.rustorio.domain.building.SpeedModule}
 * without demolishing the underlying building.
 */
class UpgradeSpeedActionTest {

    @Test
    void upgradingWrapsTheBuildingAndUndoUnwrapsIt() {
        World world = new World(4, 4);
        world.placeFurnace(1, 1, Direction.RIGHT);
        UpgradeSpeedAction action = new UpgradeSpeedAction(1, 1);

        assertTrue(action.apply(world));
        assertEquals(1, world.peek(1, 1).orElseThrow().speedLevel());

        action.undo(world);
        assertEquals(0, world.peek(1, 1).orElseThrow().speedLevel());
    }

    @Test
    void upgradingABeltIsRefused() {
        World world = new World(4, 4);
        world.placeBelt(1, 1, Direction.RIGHT);

        assertFalse(new UpgradeSpeedAction(1, 1).apply(world), "belts are refused — see the class javadoc's P2-03 note");
        assertEquals(0, world.peek(1, 1).orElseThrow().speedLevel());
    }

    /**
     * (Code review finding, CODE_REVIEW_2026-07-28.md) {@code ASSEMBLER} occupies 2x2 cells —
     * upgrading from a non-anchor cell must wrap the SAME building in place, not re-anchor it at
     * the clicked cell (which would corrupt the occupancy map). Same discipline {@link
     * RotateAction}/{@link RemoveAction} already follow via {@code World#originOf}.
     */
    @Test
    void upgradingAMultiCellBuildingFromANonAnchorCellWrapsItInPlace() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        assertTrue(new UpgradeSpeedAction(3, 3).apply(world), "clicked the far corner, not the anchor");

        Building upgraded = world.peek(2, 2).orElseThrow();
        assertEquals(1, upgraded.speedLevel(), "the wrapped building must still be anchored at (2,2)");
        assertTrue(world.peek(2, 2).orElseThrow() == world.peek(3, 3).orElseThrow(),
                "still the same one building, still spanning all four original cells");
        assertTrue(world.isFree(4, 2), "must not have relocated onto neighboring cells");
        assertTrue(world.isFree(2, 4), "must not have relocated onto neighboring cells");
    }

    @Test
    void undoOfAMultiCellUpgradeTriggeredFromANonAnchorCellRestoresAtTheAnchor() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);
        UpgradeSpeedAction action = new UpgradeSpeedAction(3, 2); // top-right cell, still not the anchor

        action.apply(world);
        action.undo(world);

        Building restored = world.peek(2, 2).orElseThrow();
        assertEquals(0, restored.speedLevel());
        assertEquals(Optional.of(Direction.RIGHT), restored.outputDirection());
        assertTrue(world.isFree(4, 2), "undo must not have left the building shifted onto neighboring cells");
    }
}
