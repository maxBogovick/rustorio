package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BeltSegment}: assembling a straight belt run into one segment, moving cargo along it one
 * tile per world tick, and the riskiest spot of all — merging/splitting segments on build/demolish.
 */
class BeltTest {

    private static Belt beltAt(World world, int x, int y) {
        return (Belt) world.peek(x, y).orElseThrow();
    }

    @Test
    void chainOfBeltsMovesItemExactlyOneTileAtATimeToTheChest() {
        World world = new World(10, 10);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(1, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));
        Chest chest = new Chest();
        world.restoreBuilding(3, 0, chest);

        Belt tail = beltAt(world, 0, 0);
        assertTrue(tail.accept(world, VanillaItems.IRON_ORE));

        world.tick();
        assertEquals(Optional.empty(), beltAt(world, 0, 0).heldItem());
        assertEquals(Optional.of(VanillaItems.IRON_ORE), beltAt(world, 1, 0).heldItem());
        assertEquals(0, chest.count(), "за один тик груз не должен доехать дальше одной клетки");

        world.tick();
        assertEquals(Optional.of(VanillaItems.IRON_ORE), beltAt(world, 2, 0).heldItem());
        assertEquals(0, chest.count());

        world.tick();
        assertEquals(Optional.empty(), beltAt(world, 2, 0).heldItem());
        assertEquals(1, chest.count());
    }

    @Test
    void placingATileBetweenTwoSegmentsMergesThemIntoOne() {
        World world = new World(10, 10);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));
        Belt first = beltAt(world, 0, 0);
        Belt third = beltAt(world, 2, 0);
        assertNotSame(first.segment(), third.segment(), "до моста — два разных сегмента");

        assertTrue(world.placeBelt(1, 0, Direction.RIGHT));

        assertSame(first.segment(), third.segment(), "после моста — один и тот же сегмент");
        assertEquals(3, first.segment().size());
    }

    @Test
    void removingTheMiddleTileSplitsTheSegmentAndTakesItsCargoWithIt() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeBelt(1, 0, Direction.RIGHT);
        world.placeBelt(2, 0, Direction.RIGHT);
        Belt tail = beltAt(world, 0, 0);
        Belt head = beltAt(world, 2, 0);
        assertEquals(3, tail.segment().size());

        tail.accept(world, VanillaItems.IRON_ORE);
        world.tick(); // едет tail -> mid

        Building removed = world.removeBuilding(1, 0).orElseThrow();
        assertEquals(Optional.of(VanillaItems.IRON_ORE), removed.heldItem(), "груз снесённого тайла уходит вместе с ним");

        assertEquals(1, tail.segment().size(), "хвост остался один в своём куске");
        assertEquals(1, head.segment().size(), "голова стала отдельным куском в одну клетку");
        assertNotSame(tail.segment(), head.segment());
    }

    @Test
    void removingTheTailShrinksTheSegmentWithoutCreatingASplit() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeBelt(1, 0, Direction.RIGHT);
        Belt head = beltAt(world, 1, 0);

        world.removeBuilding(0, 0);

        assertEquals(1, head.segment().size());
        assertTrue(head.segment().isTail(head));
    }

    /**
     * Originally exercised via {@code UpgradeSpeedAction} (a real trigger for {@code
     * World.restoreBuilding} re-attaching an already-attached belt — see P1-02). P2-03 later
     * forbade upgrading belts outright, which closes off that specific trigger — see
     * {@link #upgradingABeltTileIsAlwaysRefusedRegardlessOfItsPositionInTheSegment}. The
     * idempotency {@code World.restoreBuilding} itself relies on is still worth guarding directly,
     * so this test now drives it without going through an action that no longer applies to belts.
     */
    @Test
    void restoringABeltAlreadyInASegmentDoesNotDuplicateIt() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeBelt(1, 0, Direction.RIGHT);
        world.placeBelt(2, 0, Direction.RIGHT);
        Belt tail = beltAt(world, 0, 0);
        Belt middle = beltAt(world, 1, 0);
        assertEquals(3, tail.segment().size());

        world.restoreBuilding(1, 0, middle); // already attached — must not be attached a second time

        assertEquals(3, tail.segment().size(), "re-restoring an already-attached belt must not duplicate it");
    }

    @Test
    void upgradingABeltTileIsAlwaysRefusedRegardlessOfItsPositionInTheSegment() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT); // tail
        world.placeBelt(1, 0, Direction.RIGHT); // non-tail (head)

        ActionHistory history = new ActionHistory();
        history.perform(world, new UpgradeSpeedAction(0, 0)); // the tail — where the module WOULD do something
        history.perform(world, new UpgradeSpeedAction(1, 0)); // a non-tail tile — where it would be a no-op

        // P2-03 (owner decision A): refused everywhere, consistently — not just where it would be
        // a silent no-op. A player can't tell tail from non-tail, so "sometimes doubles the whole
        // segment, sometimes does nothing" isn't a real feature to leave half-supported.
        assertEquals(0, beltAt(world, 0, 0).speedLevel());
        assertEquals(0, beltAt(world, 1, 0).speedLevel());
        assertEquals(VanillaBuildings.idFor(BuildingType.BELT), world.peek(0, 0).orElseThrow().prototypeId());
    }

    /**
     * Fixed in P3-03, BUG_FIX_PROGRESS.md: a DOWN segment ticks (and hands cargo off) in the
     * world's descending pass; the LEFT segment it hands off to no longer gets to move that same
     * item again in the ascending pass of the very same frame — {@code Belt.arrivedThisTick} marks
     * it, and {@code BeltSegment.tick} refuses to move a marked tile until the mark clears at the
     * start of the NEXT world tick.
     */
    @Test
    void cargoCrossingASegmentBoundaryMovesAtMostOneTilePerTick() {
        World world = new World(4, 4);
        world.placeBelt(1, 0, Direction.DOWN);
        world.placeBelt(1, 1, Direction.LEFT);
        Chest chest = new Chest();
        world.restoreBuilding(0, 1, chest);

        Belt entry = beltAt(world, 1, 0);
        assertTrue(entry.accept(world, VanillaItems.IRON_ORE));

        world.tick();

        assertEquals(0, chest.count(), "cargo must move at most one tile per world.tick()");
    }
}
