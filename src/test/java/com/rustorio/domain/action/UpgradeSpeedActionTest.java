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
 * {@link UpgradeSpeedAction}: raise/lower a building's {@code speedLevel} in place, without
 * demolishing it.
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
     * No regression test covered this before {@code acceptsSpeedEffects} replaced the old
     * {@code instanceof} chain (found while reading the existing suite, not assumed) — an
     * {@code UndergroundBelt} half is one of the six kinds the class javadoc's N13 note refuses,
     * for the same "second tick() call is a provable no-op" reason as a Belt.
     */
    @Test
    void upgradingAnUndergroundBeltHalfIsRefused() {
        World world = new World(4, 4);
        world.placeUndergroundIn(1, 1, Direction.RIGHT);

        assertFalse(new UpgradeSpeedAction(1, 1).apply(world), "an UndergroundBelt half must be refused, same reason as a Belt");
        assertEquals(0, world.peek(1, 1).orElseThrow().speedLevel());
    }

    /**
     * Also not covered before {@code acceptsSpeedEffects} (found while reading, not assumed): a
     * {@code Chest} is NOT in the refusal list today, correctly — {@code Chest.tick} genuinely
     * pushes one item per tick (D-01), so doubling that call is a real speed effect, unlike the
     * six no-op kinds refused above.
     */
    @Test
    void upgradingAChestIsAccepted() {
        World world = new World(4, 4);
        world.placeChest(1, 1);

        assertTrue(new UpgradeSpeedAction(1, 1).apply(world), "a Chest's tick genuinely does more work when doubled — must be accepted");
        assertEquals(1, world.peek(1, 1).orElseThrow().speedLevel());
    }

    /**
     * (Code review finding) {@code Inserter}/{@code Filter}/{@code Splitter} all share the belt's
     * single-slot "held + arrivedThisTick" shape — a doubled {@code tick} call in the same world
     * tick always finds {@code held == null}, provably a no-op, same as the already-refused {@code
     * Belt}/{@code UndergroundBelt}. Before this fix the player could pay for
     * the upgrade and get nothing for it.
     */
    @Test
    void upgradingAnInserterFilterOrSplitterIsRefused() {
        World world = new World(6, 6);
        world.placeInserter(0, 0, Direction.RIGHT);
        world.placeFilter(1, 0, Direction.RIGHT);
        world.placeSplitter(2, 0, Direction.RIGHT);

        assertFalse(new UpgradeSpeedAction(0, 0).apply(world), "an Inserter must be refused, same reason as a Belt");
        assertFalse(new UpgradeSpeedAction(1, 0).apply(world), "a Filter must be refused, same reason as a Belt");
        assertFalse(new UpgradeSpeedAction(2, 0).apply(world), "a Splitter must be refused, same reason as a Belt");

        assertEquals(0, world.peek(0, 0).orElseThrow().speedLevel());
        assertEquals(0, world.peek(1, 0).orElseThrow().speedLevel());
        assertEquals(0, world.peek(2, 0).orElseThrow().speedLevel());
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
