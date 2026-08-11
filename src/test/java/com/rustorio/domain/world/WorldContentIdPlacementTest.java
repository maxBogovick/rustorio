package com.rustorio.domain.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;
import org.junit.jupiter.api.Test;

/**
 * {@link World#place(ContentId, int, int, Direction)} — the phase's own opening of the ONE place
 * construction actually happens to any registered prototype, not just the closed {@link
 * BuildingType} set. Previous phases proved a modded prototype/transport node could live in a real
 * {@code World} only via {@code world.restoreBuilding} (bypassing the placement-rule/geometry check
 * {@code place} itself makes) — this is the first test to go through the real door.
 */
class WorldContentIdPlacementTest {

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

    @Test
    void placesAModdedPrototypeWithNoCorrespondingBuildingTypeThroughTheRealDoor() {
        World world = new World(4, 4, factoryWithSteelPress());

        boolean placed = world.place(STEEL_PRESS_ID, 0, 0, Direction.RIGHT);

        assertTrue(placed);
        Building built = world.peek(0, 0).orElseThrow();
        assertInstanceOf(Furnace.class, built);
        assertEquals(STEEL_PRESS_ID, built.prototypeId());
    }

    @Test
    void respectsThePlacementRuleOfTheModdedPrototypeNotJustGeometry() {
        // A modded prototype whose PlacementRule refuses everywhere — proves World.place actually
        // consults ITS OWN rule, not a hardcoded "always passable terrain" assumption for anything
        // without a BuildingType.
        ContentId refusesEverywhereId = ContentId.of("examplemod:refuses_everywhere");
        BuildingPrototype refusesEverywhere = new BuildingPrototype(
                refusesEverywhereId, "Refuses Everywhere",
                new BuildingCost(VanillaItems.IRON_PLATE, 1),
                (x, y, oreLayout) -> false,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.BELT)).texture(),
                0, 1, false,
                (self, direction, factory) -> new com.rustorio.domain.building.Belt(direction),
                (self, decodedState, factory) -> {
                    throw new UnsupportedOperationException("not exercised by this test");
                },
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.BELT)).codec());
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        prototypes.register(refusesEverywhereId, refusesEverywhere);
        prototypes.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);
        World world = new World(4, 4, factory);

        assertFalse(world.place(refusesEverywhereId, 0, 0, Direction.RIGHT));
        assertTrue(world.peek(0, 0).isEmpty());
    }

    @Test
    void trySpendAndRefundBuildingCostWorkForAModdedPrototypeToo() {
        World world = new World(4, 4, factoryWithSteelPress());

        assertTrue(world.trySpendBuildingCost(STEEL_PRESS_ID));
        assertEquals(30 - 20, world.inventory().amount(VanillaItems.IRON_PLATE),
                "starting inventory (30 iron plate) minus the modded prototype's own cost (20)");

        world.refundBuildingCost(STEEL_PRESS_ID);
        assertEquals(30, world.inventory().amount(VanillaItems.IRON_PLATE));
    }

    /** The BuildingType-based overloads must still behave exactly as before — pure delegation, not a behavior change. */
    @Test
    void buildingTypeOverloadsStillWorkUnchanged() {
        World world = new World(4, 4);

        assertTrue(world.place(BuildingType.CHEST, 1, 0, Direction.RIGHT));
        assertTrue(world.peek(1, 0).isPresent());
    }
}
