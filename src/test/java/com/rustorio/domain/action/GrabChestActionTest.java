package com.rustorio.domain.action;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GrabChestAction}: a live bug report — a chest's contents and the player's own buildable
 * stock used to be two completely disconnected pools, with no way to move items from one into the
 * other. A factory that had produced plenty could still be unable to afford its own next building.
 */
class GrabChestActionTest {

    @Test
    void grabbingCreditsEveryKindTheChestWasHoldingAndEmptiesIt() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.IRON_ORE);
        chest.accept(world, VanillaItems.IRON_ORE);
        chest.accept(world, VanillaItems.GEAR);
        int oreBefore = world.inventory().amount(VanillaItems.IRON_ORE);
        int gearBefore = world.inventory().amount(VanillaItems.GEAR);

        assertTrue(new GrabChestAction(1, 1).apply(world));

        assertEquals(oreBefore + 2, world.inventory().amount(VanillaItems.IRON_ORE));
        assertEquals(gearBefore + 1, world.inventory().amount(VanillaItems.GEAR));
        assertEquals(0, chest.count(), "the chest itself must be empty after a grab");
    }

    @Test
    void chestStaysStandingAfterAGrab() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);

        new GrabChestAction(1, 1).apply(world);

        assertTrue(world.peek(1, 1).isPresent(), "grabbing must not demolish the chest — only RemoveAction does that");
    }

    @Test
    void grabbingAnEmptyChestFails() {
        World world = new World(4, 4);
        world.restoreBuilding(1, 1, new Chest());

        assertFalse(new GrabChestAction(1, 1).apply(world), "nothing to grab — not worth an undo entry either");
    }

    @Test
    void grabbingAnythingOtherThanAChestFails() {
        World world = new World(4, 4);
        world.placeBelt(1, 1, Direction.RIGHT);

        assertFalse(new GrabChestAction(1, 1).apply(world));
    }

    @Test
    void grabbingAnEmptyCellFails() {
        World world = new World(4, 4);

        assertFalse(new GrabChestAction(1, 1).apply(world));
    }

    @Test
    void undoReturnsTheContentsToTheChestAndReclaimsTheCredit() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);
        chest.accept(world, VanillaItems.GEAR);
        int gearBefore = world.inventory().amount(VanillaItems.GEAR);
        GrabChestAction grab = new GrabChestAction(1, 1);

        grab.apply(world);
        grab.undo(world);

        assertEquals(gearBefore, world.inventory().amount(VanillaItems.GEAR),
                "undo must claw back exactly what the grab credited");
        assertEquals(2, chest.amount(VanillaItems.GEAR), "and hand it back to the same chest");
    }

    @Test
    void undoRefusesWhenThePlayerAlreadySpentWhatWasGrabbed() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);
        GrabChestAction grab = new GrabChestAction(1, 1);
        grab.apply(world);

        assertTrue(world.trySpendItems(Map.of(VanillaItems.GEAR, 1)), "simulate spending the grabbed GEAR elsewhere");

        grab.undo(world);

        assertEquals(0, chest.amount(VanillaItems.GEAR), "undo must refuse — nothing left to pay back into the chest");
    }
}
