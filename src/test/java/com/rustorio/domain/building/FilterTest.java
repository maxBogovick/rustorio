package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Filter} (X-01, DEV_TASKS.md): the item-identity half of the old combined {@code
 * Splitter} — routes by a player-CHOSEN {@link ItemType}, not a hardcoded {@code SortRule} (deleted).
 */
class FilterTest {

    @Test
    void matchingItemGoesForward() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Filter(Direction.RIGHT, VanillaItems.IRON_ORE));
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side);

        Filter filter = (Filter) world.peek(1, 1).orElseThrow();
        assertTrue(filter.accept(world, VanillaItems.IRON_ORE));

        world.tick();
        assertEquals(1, forward.count());
        assertEquals(0, side.count());
    }

    @Test
    void nonMatchingItemGoesToTheRotatedSide() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Filter(Direction.RIGHT, VanillaItems.IRON_ORE));
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side);

        Filter filter = (Filter) world.peek(1, 1).orElseThrow();
        assertTrue(filter.accept(world, VanillaItems.GEAR));

        world.tick();
        assertEquals(0, forward.count());
        assertEquals(1, side.count());
    }

    @Test
    void cycleFilterItemAdvancesThroughEveryItemAndWrapsAround() {
        Registry<ItemType> items = VanillaItems.frozen();
        Filter filter = new Filter(Direction.RIGHT, items.get(items.size() - 1));

        ItemType next = filter.cycleFilterItem();

        assertEquals(items.get(0), next, "must wrap back to the first item");
        assertEquals(items.get(0), filter.filterItem());
    }

    @Test
    void changingTheFilterItemChangesWhatCountsAsAMatch() {
        World world = new World(10, 10);
        world.restoreBuilding(1, 1, new Filter(Direction.RIGHT, VanillaItems.IRON_ORE));
        Chest forward = new Chest();
        world.restoreBuilding(2, 1, forward);
        Chest side = new Chest(Direction.LEFT);
        world.restoreBuilding(1, 2, side);

        Filter filter = (Filter) world.peek(1, 1).orElseThrow();
        while (filter.filterItem() != VanillaItems.GEAR) {
            filter.cycleFilterItem();
        }

        assertTrue(filter.accept(world, VanillaItems.GEAR));
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
        world.restoreBuilding(11, 10, new Filter(Direction.LEFT, VanillaItems.IRON_ORE));
        world.restoreBuilding(10, 10, new Filter(Direction.LEFT, VanillaItems.IRON_ORE));
        Chest target = new Chest(Direction.UP); // faces away — a passive receiver
        world.restoreBuilding(9, 10, target);

        Filter upstream = (Filter) world.peek(11, 10).orElseThrow();
        Filter downstream = (Filter) world.peek(10, 10).orElseThrow();
        assertTrue(upstream.accept(world, VanillaItems.IRON_ORE));

        world.tick();

        assertEquals(0, target.count(), "the item must not cross two filters in one tick");
        assertEquals(Optional.of(VanillaItems.IRON_ORE), downstream.heldItem(), "it settles on the middle filter first");

        world.tick();

        assertEquals(1, target.count(), "and moves on the NEXT tick");
    }

    /**
     * Regression test for code review finding S2: {@link Filter#cycleFilterItem} used to always
     * walk a hardcoded {@code VanillaItems.frozen()}, so a modded item could never appear when
     * cycling, no matter what registry the filter was actually built with.
     */
    @Test
    void cycleFilterItemWalksTheInjectedRegistryNotTheHardcodedVanillaOne() {
        ItemType copperOre = new ItemType(ContentId.of("test:copper_ore"), "Copper Ore", false, 0, ItemShape.CIRCLE);
        ItemType tinOre = new ItemType(ContentId.of("test:tin_ore"), "Tin Ore", false, 0, ItemShape.CIRCLE);
        Registry<ItemType> modded = new Registry<>();
        modded.register(copperOre.id(), copperOre);
        modded.register(tinOre.id(), tinOre);
        modded.freeze();

        Filter filter = new Filter(Direction.RIGHT, copperOre, modded);

        ItemType next = filter.cycleFilterItem();

        assertEquals(tinOre, next, "must cycle within the injected registry's own items, not vanilla's 11");
    }

    @Test
    void rotatingRotatesBothOutputsAndKeepsTheFilterItem() {
        Filter filter = new Filter(Direction.RIGHT, VanillaItems.GEAR);

        Filter rotated = (Filter) filter.rotatedClockwise().orElseThrow();

        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(Optional.of(Direction.LEFT), rotated.secondaryOutputDirection());
        assertEquals(VanillaItems.GEAR, rotated.filterItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesFacingCargoAndFilterItem() {
        Filter original = new Filter(Direction.UP, VanillaItems.BRONZE_PLATE);
        original.accept(null, VanillaItems.GEAR);

        FilterState state = (FilterState) original.state();
        Filter reloaded = new Filter(state.facing(), state.filterItem(), state.held(), VanillaItems.frozen());

        assertEquals(Optional.of(Direction.UP), reloaded.outputDirection());
        assertEquals(Optional.of(VanillaItems.GEAR), reloaded.heldItem());
        assertEquals(VanillaItems.BRONZE_PLATE, reloaded.filterItem());
    }
}
