package com.rustorio.domain.action;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (D-03, DEV_TASKS.md) {@link PlaceAction}: placing a building now costs resources from {@link
 * World#inventory()} — this closes the loop §1 of the design audit calls the game's core defect,
 * production finally has somewhere to matter besides the research counter.
 */
class PlaceActionTest {

    private static BuildingCost costFor(BuildingType type) {
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type)).cost();
    }

    @Test
    void placingChargesTheBuildingsCost() {
        World world = new World(4, 4);
        BuildingCost cost = costFor(BuildingType.CHEST);
        int before = world.inventory().amount(cost.item());

        assertTrue(new PlaceAction(BuildingType.CHEST, 1, 1).apply(world));

        assertEquals(before - cost.amount(), world.inventory().amount(cost.item()));
    }

    @Test
    void cannotAffordMeansNothingIsChargedAndNothingIsBuilt() {
        World world = new World(4, 4);
        BuildingCost labCost = costFor(BuildingType.LAB); // GEAR — the player starts with none
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
        BuildingCost cost = costFor(BuildingType.CHEST);
        int before = world.inventory().amount(cost.item());

        assertFalse(new PlaceAction(BuildingType.CHEST, 1, 1).apply(world),
                "the cell is occupied — World.place must refuse regardless of affordability");

        assertEquals(before, world.inventory().amount(cost.item()),
                "a charge taken before a failed placement must be refunded, net zero");
    }

    @Test
    void undoRefundsExactlyWhatApplyCharged() {
        World world = new World(4, 4);
        BuildingCost cost = costFor(BuildingType.BELT);
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
        assertTrue(chest.accept(world, VanillaItems.GEAR));
        assertTrue(chest.accept(world, VanillaItems.GEAR));
        assertTrue(chest.accept(world, VanillaItems.COAL));
        int gearBefore = world.inventory().amount(VanillaItems.GEAR);
        int coalBefore = world.inventory().amount(VanillaItems.COAL);

        action.undo(world);

        assertFalse(world.peek(1, 1).isPresent());
        assertEquals(gearBefore + 2, world.inventory().amount(VanillaItems.GEAR), "stored GEAR must come back to the player");
        assertEquals(coalBefore + 1, world.inventory().amount(VanillaItems.COAL), "and so must every other kind");
    }

    /**
     * (Code review finding) Redo used to always call {@code World.place}, which builds a brand-new,
     * EMPTY {@link Chest} — after undo credited a chest's contents to inventory (see the test
     * above), a subsequent redo silently left the chest empty on the map while the credited items
     * stayed spendable in inventory too, an asymmetric undo/redo pair unlike every other multi-step
     * action in this package (contrast {@code RemoveAction}/{@code RotateAction}, which restore the
     * exact same object). This pins the round trip down: redo must claim the saved contents back
     * out of inventory and put them back in the (new) chest.
     */
    @Test
    void redoOfAPlacedChestRestoresWhatUndoHadDrainedFromIt() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();
        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1));

        Chest chest = (Chest) world.peek(1, 1).orElseThrow();
        assertTrue(chest.accept(world, VanillaItems.IRON_ORE));
        assertTrue(chest.accept(world, VanillaItems.IRON_ORE));
        assertTrue(chest.accept(world, VanillaItems.GEAR));

        history.undo(world);
        assertFalse(world.peek(1, 1).isPresent(), "sanity check — the chest must be gone after undo");
        int ironOreAfterUndo = world.inventory().amount(VanillaItems.IRON_ORE);
        int gearAfterUndo = world.inventory().amount(VanillaItems.GEAR);
        assertEquals(2, ironOreAfterUndo, "undo must have credited the drained IRON_ORE to inventory");

        history.redo(world);

        Chest restored = (Chest) world.peek(1, 1).orElseThrow();
        assertEquals(2, restored.amount(VanillaItems.IRON_ORE), "redo must restore what undo drained, not a blank chest");
        assertEquals(1, restored.amount(VanillaItems.GEAR));
        assertEquals(ironOreAfterUndo - 2, world.inventory().amount(VanillaItems.IRON_ORE),
                "the restored contents must be claimed back OUT of inventory, not duplicated");
        assertEquals(gearAfterUndo - 1, world.inventory().amount(VanillaItems.GEAR));
    }

    /**
     * If the player already spent what undo credited them, redo can't fully restore the old
     * state — it must NOT dip below zero or fabricate items; the chest it places just stays empty,
     * the same "stays applied" compromise {@code RemoveAction#undo}/{@code GrabChestAction#undo}
     * already make on their own side of the stack.
     */
    @Test
    void redoLeavesTheChestEmptyIfThePlayerAlreadySpentWhatUndoCredited() {
        World world = new World(4, 4);
        ActionHistory history = new ActionHistory();
        history.perform(world, new PlaceAction(BuildingType.CHEST, 1, 1));
        Chest chest = (Chest) world.peek(1, 1).orElseThrow();
        assertTrue(chest.accept(world, VanillaItems.GEAR));

        history.undo(world);
        // Spend exactly the one credited GEAR on something else before redoing.
        assertTrue(world.trySpendItems(Map.of(VanillaItems.GEAR, 1)));

        history.redo(world);

        Chest restored = (Chest) world.peek(1, 1).orElseThrow();
        assertEquals(0, restored.amount(VanillaItems.GEAR), "nothing left to restore — the player already spent it");
    }

    /**
     * The deadlock this balance decision exists to avoid: {@code PRESS} must cost only {@code
     * IRON_PLATE}, never {@code GEAR} — {@code GEAR} is only produced BY a press, so pricing a
     * press in gear would make the very first one unbuildable. {@code LAB}, built after a press is
     * already assumed running, is the one building priced in the refined good instead.
     */
    @Test
    void pressCostsPlateNotGearToAvoidABootstrapDeadlock() {
        BuildingCost pressCost = costFor(BuildingType.PRESS);
        BuildingCost labCost = costFor(BuildingType.LAB);

        assertEquals(VanillaItems.IRON_PLATE, pressCost.item());
        assertEquals(VanillaItems.GEAR, labCost.item());
    }

    private static final ContentId STEEL_PRESS_ID = ContentId.of("examplemod:steel_press");

    private static BuildingFactory factoryWithSteelPress() {
        BuildingPrototype steelPress = new BuildingPrototype(
                STEEL_PRESS_ID, "Steel Press",
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).texture(),
                10, 2, true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    throw new UnsupportedOperationException("not exercised by this test");
                },
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec());
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        prototypes.register(STEEL_PRESS_ID, steelPress);
        prototypes.freeze();
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);
    }

    /**
     * (E8-03) A {@link ContentId} constructor, not just {@link BuildingType} — a modded prototype
     * with no {@code BuildingType} of its own must be placeable through the SAME action a player's
     * real click goes through (cost charged, undo refunds it), not a bypass.
     */
    @Test
    void contentIdConstructorChargesAndPlacesAModdedPrototype() {
        World world = new World(4, 4, factoryWithSteelPress());
        int before = world.inventory().amount(VanillaItems.IRON_PLATE);
        PlaceAction action = new PlaceAction(STEEL_PRESS_ID, 1, 1, Direction.RIGHT);

        assertTrue(action.apply(world));

        assertEquals(before - 20, world.inventory().amount(VanillaItems.IRON_PLATE));
        Building built = world.peek(1, 1).orElseThrow();
        assertInstanceOf(Furnace.class, built);
        assertEquals(STEEL_PRESS_ID, built.prototypeId());

        action.undo(world);

        assertFalse(world.peek(1, 1).isPresent());
        assertEquals(before, world.inventory().amount(VanillaItems.IRON_PLATE), "undo must refund the modded prototype's own cost");
    }
}
