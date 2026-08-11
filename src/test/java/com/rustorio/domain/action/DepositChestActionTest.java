package com.rustorio.domain.action;

import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DepositChestAction}: inventory → chest, the missing half of the grab loop. Without it the
 * player can empty a chest into the buildable stock but cannot put anything back onto a belt line
 * by hand.
 */
class DepositChestActionTest {

    @Test
    void depositingMovesAsManyAsFitFromInventoryIntoTheChest() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        world.creditItem(VanillaItems.GEAR, 5);

        assertTrue(new DepositChestAction(1, 1, VanillaItems.GEAR).apply(world));

        assertEquals(0, world.inventory().amount(VanillaItems.GEAR));
        assertEquals(5, chest.amount(VanillaItems.GEAR));
    }

    @Test
    void depositingStopsAtChestCapacity() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        // Fill almost full — vanilla chest capacity is 100 without BIG_BUFFER.
        for (int i = 0; i < 98; i++) {
            assertTrue(chest.accept(world, VanillaItems.COAL));
        }
        world.creditItem(VanillaItems.GEAR, 10);

        assertTrue(new DepositChestAction(1, 1, VanillaItems.GEAR).apply(world));

        assertEquals(2, chest.amount(VanillaItems.GEAR), "only the free slots");
        assertEquals(8, world.inventory().amount(VanillaItems.GEAR));
    }

    @Test
    void depositingWithNothingSelectedFails() {
        World world = new World(4, 4);
        world.restoreBuilding(1, 1, new Chest());

        assertFalse(new DepositChestAction(1, 1, VanillaItems.GEAR).apply(world));
    }

    @Test
    void depositingOntoABeltFails() {
        World world = new World(4, 4);
        world.placeBelt(1, 1, Direction.RIGHT);
        world.creditItem(VanillaItems.GEAR, 1);

        assertFalse(new DepositChestAction(1, 1, VanillaItems.GEAR).apply(world));
    }

    @Test
    void undoReturnsTheStackToInventory() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        world.creditItem(VanillaItems.GEAR, 3);
        int before = world.inventory().amount(VanillaItems.GEAR);
        DepositChestAction deposit = new DepositChestAction(1, 1, VanillaItems.GEAR);

        deposit.apply(world);
        deposit.undo(world);

        assertEquals(before, world.inventory().amount(VanillaItems.GEAR));
        assertEquals(0, chest.amount(VanillaItems.GEAR));
    }
}
