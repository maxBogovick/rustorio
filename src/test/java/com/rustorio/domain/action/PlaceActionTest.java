package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (D-03, DEV_TASKS.md) {@link PlaceAction}: placing a building now costs resources from {@link
 * World#inventory()} — this closes the loop §1 of the design audit calls the game's core defect,
 * production finally has somewhere to matter besides the research counter.
 */
class PlaceActionTest {

    @Test
    void placingChargesTheBuildingsCost() {
        World world = new World(4, 4);
        BuildingCost cost = BuildingCost.forType(BuildingType.CHEST);
        int before = world.inventory().amount(cost.item());

        assertTrue(new PlaceAction(BuildingType.CHEST, 1, 1).apply(world));

        assertEquals(before - cost.amount(), world.inventory().amount(cost.item()));
    }

    @Test
    void cannotAffordMeansNothingIsChargedAndNothingIsBuilt() {
        World world = new World(4, 4);
        BuildingCost labCost = BuildingCost.forType(BuildingType.LAB); // GEAR — the player starts with none
        int before = world.inventory().amount(labCost.item());

        assertFalse(new PlaceAction(BuildingType.LAB, 1, 1).apply(world),
                "the player starts with no GEAR at all, so a lab must be unaffordable");

        assertFalse(world.peek(1, 1).isPresent());
        assertEquals(before, world.inventory().amount(labCost.item()), "an unaffordable placement must not spend anything");
    }

    @Test
    void placementFailingForAnUnrelatedReasonRefundsTheCharge() {
        World world = new World(4, 4);
        world.placeChest(1, 1); // occupy the cell directly, for free, ahead of time
        BuildingCost cost = BuildingCost.forType(BuildingType.CHEST);
        int before = world.inventory().amount(cost.item());

        assertFalse(new PlaceAction(BuildingType.CHEST, 1, 1).apply(world),
                "the cell is occupied — World.place must refuse regardless of affordability");

        assertEquals(before, world.inventory().amount(cost.item()),
                "a charge taken before a failed placement must be refunded, net zero");
    }

    @Test
    void undoRefundsExactlyWhatApplyCharged() {
        World world = new World(4, 4);
        BuildingCost cost = BuildingCost.forType(BuildingType.BELT);
        int before = world.inventory().amount(cost.item());
        PlaceAction action = new PlaceAction(BuildingType.BELT, 1, 1, Direction.RIGHT);

        assertTrue(action.apply(world));
        action.undo(world);

        assertFalse(world.peek(1, 1).isPresent());
        assertEquals(before, world.inventory().amount(cost.item()));
    }

    /**
     * Undoing a placement must not incinerate what the building accumulated in the meantime (N7,
     * NEW_BUGS_PROGRESS.md). {@code RemoveAction} already refunds a demolished chest's contents (a
     * live bug report of its own); {@code Ctrl+Z} over the same chest used to be a silent hole in
     * the map that swallowed everything inside it.
     */
    @Test
    void undoOfAPlacedChestReturnsItsContentsInsteadOfDestroyingThem() {
        World world = new World(4, 4);
        PlaceAction action = new PlaceAction(BuildingType.CHEST, 1, 1);
        assertTrue(action.apply(world));

        Chest chest = (Chest) world.peek(1, 1).orElseThrow();
        assertTrue(chest.accept(world, Item.GEAR));
        assertTrue(chest.accept(world, Item.GEAR));
        assertTrue(chest.accept(world, Item.COAL));
        int gearBefore = world.inventory().amount(Item.GEAR);
        int coalBefore = world.inventory().amount(Item.COAL);

        action.undo(world);

        assertFalse(world.peek(1, 1).isPresent());
        assertEquals(gearBefore + 2, world.inventory().amount(Item.GEAR), "stored GEAR must come back to the player");
        assertEquals(coalBefore + 1, world.inventory().amount(Item.COAL), "and so must every other kind");
    }

    /**
     * The deadlock this balance decision exists to avoid: {@code PRESS} must cost only {@code
     * IRON_PLATE}, never {@code GEAR} — {@code GEAR} is only produced BY a press, so pricing a
     * press in gear would make the very first one unbuildable. {@code LAB}, built after a press is
     * already assumed running, is the one building priced in the refined good instead.
     */
    @Test
    void pressCostsPlateNotGearToAvoidABootstrapDeadlock() {
        BuildingCost pressCost = BuildingCost.forType(BuildingType.PRESS);
        BuildingCost labCost = BuildingCost.forType(BuildingType.LAB);

        assertEquals(Item.IRON_PLATE, pressCost.item());
        assertEquals(Item.GEAR, labCost.item());
    }
}
