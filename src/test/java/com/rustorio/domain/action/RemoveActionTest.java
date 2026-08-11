package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (D-03, DEV_TASKS.md) {@link RemoveAction}: demolition refunds a building's {@link BuildingCost} —
 * §8.4 of the design audit's own addition, "if a building costs resources, demolishing it must
 * return them, or Ctrl+Z stops being economically safe."
 */
class RemoveActionTest {

    @Test
    void demolishingRefundsTheBuildingsCost() {
        World world = new World(4, 4);
        BuildingCost cost = world.buildingFactory().prototype(BuildingType.CHEST).cost();
        world.placeChest(1, 1); // placed directly (free) — this test is about the REFUND, not the charge
        int before = world.inventory().amount(cost.item());

        assertTrue(new RemoveAction(1, 1).apply(world));

        assertEquals(before + cost.amount(), world.inventory().amount(cost.item()));
    }

    @Test
    void undoOfADemolitionRechargesTheCostAndRestoresTheBuilding() {
        World world = new World(4, 4);
        BuildingCost cost = world.buildingFactory().prototype(BuildingType.BELT).cost();
        world.placeBelt(1, 1, Direction.RIGHT);
        int beforeRemoval = world.inventory().amount(cost.item());
        RemoveAction action = new RemoveAction(1, 1);

        action.apply(world); // refunds cost.amount()
        action.undo(world); // must re-charge that same amount and put the belt back

        assertTrue(world.peek(1, 1).isPresent());
        assertEquals(beforeRemoval, world.inventory().amount(cost.item()),
                "apply's refund and undo's re-charge must cancel out exactly");
    }

    /**
     * If the player spent the refund on something else before undoing the demolition, {@code Ctrl+Z}
     * must not hand the building back anyway — that would be resources from nowhere.
     */
    @Test
    void undoRefusesWhenThePlayerCanNoLongerAffordTheRecharge() {
        World world = new World(50, 4); // wide enough for up to ~30 belts in a row, see below
        world.placeBelt(1, 1, Direction.RIGHT);
        RemoveAction remove = new RemoveAction(1, 1);
        remove.apply(world); // refunds 1 IRON_PLATE

        // Spend every last plate on something else before attempting the undo.
        int plates = world.inventory().amount(VanillaItems.IRON_PLATE);
        for (int x = 0; x < plates; x++) {
            new PlaceAction(BuildingType.BELT, 2 + x, 1, Direction.RIGHT).apply(world);
        }
        assertEquals(0, world.inventory().amount(VanillaItems.IRON_PLATE));

        remove.undo(world);

        assertFalse(world.peek(1, 1).isPresent(),
                "undo must refuse — the player can no longer afford to re-buy the demolished belt");
    }

    /** (Live bug report) A chest's contents used to just vanish on demolition — only its own construction cost was refunded. */
    @Test
    void demolishingAChestRefundsItsContentsToo() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);
        chest.accept(world, VanillaItems.GEAR);
        chest.accept(world, VanillaItems.IRON_ORE);
        int gearBefore = world.inventory().amount(VanillaItems.GEAR);
        int oreBefore = world.inventory().amount(VanillaItems.IRON_ORE);

        assertTrue(new RemoveAction(1, 1).apply(world));

        assertEquals(gearBefore + 2, world.inventory().amount(VanillaItems.GEAR));
        assertEquals(oreBefore + 1, world.inventory().amount(VanillaItems.IRON_ORE));
    }

    @Test
    void undoOfADemolishedChestReclaimsTheContentsBack() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);
        int gearBefore = world.inventory().amount(VanillaItems.GEAR);
        RemoveAction remove = new RemoveAction(1, 1);

        remove.apply(world); // credits 1 GEAR, refunds the chest's own IRON_PLATE cost
        remove.undo(world);

        assertTrue(world.peek(1, 1).isPresent());
        assertEquals(gearBefore, world.inventory().amount(VanillaItems.GEAR),
                "undo must claw back exactly the GEAR that demolition credited");
        Chest restored = (Chest) world.peek(1, 1).orElseThrow();
        assertEquals(1, restored.amount(VanillaItems.GEAR), "the restored chest must have its GEAR back too");
    }

    /**
     * The building's own cost (IRON_PLATE) and a demolished chest's reclaimed contents (GEAR here)
     * are checked separately — a player who can afford one but not the other must still see the
     * WHOLE undo refused, with whatever it already charged rolled back.
     */
    @Test
    void undoRefusesWhenThePlayerCanAffordTheChestButNotItsReclaimedContents() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);
        RemoveAction remove = new RemoveAction(1, 1);
        remove.apply(world); // credits 1 GEAR, refunds the chest's own IRON_PLATE cost
        int platesAfterApply = world.inventory().amount(VanillaItems.IRON_PLATE);

        assertTrue(world.trySpendItems(Map.of(VanillaItems.GEAR, 1)), "simulate having spent the reclaimed GEAR elsewhere");

        remove.undo(world);

        assertFalse(world.peek(1, 1).isPresent(), "undo must refuse — the player can't pay back the reclaimed GEAR");
        assertEquals(platesAfterApply, world.inventory().amount(VanillaItems.IRON_PLATE),
                "the building-cost charge taken during the failed undo attempt must have been rolled back");
    }

    /**
     * (X-03, DEV_TASKS.md) {@code ASSEMBLER} occupies 2x2 cells — demolishing via ANY of its four
     * cells (not only the anchor) must free the whole footprint, and undo must restore it whole,
     * at its original position, not wherever the demolishing click happened to land (see {@link
     * RemoveAction}'s own javadoc on {@code anchorX}/{@code anchorY}).
     */
    @Test
    void demolishingAMultiCellBuildingFromANonAnchorCellFreesTheWholeFootprint() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        assertTrue(new RemoveAction(3, 3).apply(world), "clicked the far corner, not the anchor");

        assertTrue(world.isFree(2, 2));
        assertTrue(world.isFree(3, 2));
        assertTrue(world.isFree(2, 3));
        assertTrue(world.isFree(3, 3));
    }

    @Test
    void undoOfADemolitionTriggeredFromANonAnchorCellRestoresTheWholeFootprintAtTheAnchor() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);
        RemoveAction remove = new RemoveAction(3, 2); // top-right cell, still not the anchor

        remove.apply(world);
        remove.undo(world);

        assertTrue(world.peek(2, 2).isPresent(), "anchor");
        assertTrue(world.peek(3, 2).isPresent());
        assertTrue(world.peek(2, 3).isPresent());
        assertTrue(world.peek(3, 3).isPresent());
        assertTrue(world.peek(2, 2).orElseThrow() == world.peek(3, 3).orElseThrow(), "restored as ONE building, not four");
    }

    /**
     * The multi-cell analogue of {@link #undoOfADemolitionTriggeredFromANonAnchorCellRestoresTheWholeFootprintAtTheAnchor}'s
     * opposite case: something else got built into part of the vacated footprint before Ctrl+Z —
     * undo must refuse rather than silently overwrite that new building's occupancy.
     */
    @Test
    void undoRefusesWhenAnotherBuildingNowOccupiesPartOfTheVacatedFootprint() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);
        RemoveAction remove = new RemoveAction(2, 2);
        remove.apply(world);
        world.placeChest(3, 3); // one of the four vacated cells, now occupied by something else

        remove.undo(world);

        Building atThatCell = world.peek(3, 3).orElseThrow();
        assertEquals(VanillaBuildings.idFor(BuildingType.CHEST), atThatCell.prototypeId(), "the new chest must survive the refused undo untouched");
        assertTrue(world.isFree(2, 2), "the assembler must NOT have been silently restored over the chest");
    }
}
