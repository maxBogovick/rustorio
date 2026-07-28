package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Filter} (X-01, DEV_TASKS.md): the item-identity half of the old combined {@code
 * Splitter} — routes by a player-CHOSEN {@link Item}, not a hardcoded {@code SortRule} (deleted).
 */
class FilterTest {

    @Test
    void matchingItemGoesForward() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Filter(Direction.RIGHT, Item.IRON_ORE));
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side);

        Filter filter = (Filter) world.peek(1, 1).orElseThrow();
        assertTrue(filter.accept(world, Item.IRON_ORE));

        world.tick();
        assertEquals(1, forward.count());
        assertEquals(0, side.count());
    }

    @Test
    void nonMatchingItemGoesToTheRotatedSide() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Filter(Direction.RIGHT, Item.IRON_ORE));
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side);

        Filter filter = (Filter) world.peek(1, 1).orElseThrow();
        assertTrue(filter.accept(world, Item.GEAR));

        world.tick();
        assertEquals(0, forward.count());
        assertEquals(1, side.count());
    }

    @Test
    void cycleFilterItemAdvancesThroughEveryItemAndWrapsAround() {
        Filter filter = new Filter(Direction.RIGHT, Item.values()[Item.values().length - 1]);

        Item next = filter.cycleFilterItem();

        assertEquals(Item.values()[0], next, "must wrap back to the first item");
        assertEquals(Item.values()[0], filter.filterItem());
    }

    @Test
    void changingTheFilterItemChangesWhatCountsAsAMatch() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Filter(Direction.RIGHT, Item.IRON_ORE));
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side);

        Filter filter = (Filter) world.peek(1, 1).orElseThrow();
        while (filter.filterItem() != Item.GEAR) {
            filter.cycleFilterItem();
        }

        assertTrue(filter.accept(world, Item.GEAR));
        world.tick();

        assertEquals(1, forward.count(), "GEAR now matches the (changed) filter, so it must go forward");
    }

    /**
     * One tile per tick ACROSS a chain (N2, NEW_BUGS_PROGRESS.md) — see {@code InserterTest}'s twin
     * of this test for the mechanism.
     */
    @Test
    void cargoHandedOverMidTickWaitsForTheNextTickInsteadOfCrossingTheWholeChain() {
        World world = new World(20, 20);
        world.restoreBuilding(11, 10, new Filter(Direction.LEFT, Item.IRON_ORE));
        world.restoreBuilding(10, 10, new Filter(Direction.LEFT, Item.IRON_ORE));
        Chest target = new Chest(Direction.UP); // faces away — a passive receiver
        world.restoreBuilding(9, 10, target);

        Filter upstream = (Filter) world.peek(11, 10).orElseThrow();
        Filter downstream = (Filter) world.peek(10, 10).orElseThrow();
        assertTrue(upstream.accept(world, Item.IRON_ORE));

        world.tick();

        assertEquals(0, target.count(), "the item must not cross two filters in one tick");
        assertEquals(Optional.of(Item.IRON_ORE), downstream.heldItem(), "it settles on the middle filter first");

        world.tick();

        assertEquals(1, target.count(), "and moves on the NEXT tick");
    }

    @Test
    void rotatingRotatesBothOutputsAndKeepsTheFilterItem() {
        Filter filter = new Filter(Direction.RIGHT, Item.GEAR);

        Filter rotated = (Filter) filter.rotatedClockwise().orElseThrow();

        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(Optional.of(Direction.LEFT), rotated.secondaryOutputDirection());
        assertEquals(Item.GEAR, rotated.filterItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesFacingCargoAndFilterItem() {
        Filter original = new Filter(Direction.UP, Item.BRONZE_PLATE);
        original.accept(null, Item.GEAR);

        BuildingMemento.FilterState memento = (BuildingMemento.FilterState) original.memento();
        Filter reloaded = new Filter(memento.facing(), memento.filterItem(), memento.held());

        assertEquals(Optional.of(Direction.UP), reloaded.outputDirection());
        assertEquals(Optional.of(Item.GEAR), reloaded.heldItem());
        assertEquals(Item.BRONZE_PLATE, reloaded.filterItem());
    }
}
