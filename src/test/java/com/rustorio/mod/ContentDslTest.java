package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.SimpleCrafter;
import com.rustorio.domain.building.SimpleCrafterSpec;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * API v2 phase 1: fluent {@code content()} registration must produce usable content, and
 * {@link SimpleCrafter} must cook + round-trip through its codec.
 */
class ContentDslTest {

    @Test
    void dslItemRecipeAndSimpleCrafterRegisterUsableContent() {
        GameRegistrationContext shared = new GameRegistrationContext();
        VanillaItems.registerAll(shared.items());
        ModScopedRegistrationContext ctx = new ModScopedRegistrationContext(new ModId("amberworks"), shared);

        ctx.content().item("amber_ore").label("Amber Ore").color(0xC9882A).shape(ItemShape.CIRCLE).register();
        ctx.content().item("amber_ingot").label("Amber Ingot").color(0xE8B84A).shape(ItemShape.SQUARE).register();
        ctx.content().item("amber_polished").label("Polished Amber").color(0xFFD27A)
                .shape(ItemShape.TRIANGLE).researchGrade().register();

        ctx.content().recipe("smelt_amber")
                .input("amber_ore").output("amber_ingot").time(8).inFurnace().register();

        ContentId polisher = ctx.content().building("polisher")
                .label("Amber Polisher")
                .cost("rustorio:iron_plate", 8)
                .placement("needs_passable_terrain")
                .texture(VanillaSprites.ASSEMBLER)
                .simpleCrafter("amber_ingot", "amber_polished", 3)
                .register();

        shared.items().freeze();
        shared.buildings().freeze();
        shared.recipes().freeze();
        shared.fluids().freeze();

        ItemType polished = shared.items().get(ContentId.of("amberworks:amber_polished"));
        assertTrue(polished.researchGrade());

        Recipe recipe = shared.recipes().get(ContentId.of("amberworks:smelt_amber"));
        assertEquals(BuildingType.FURNACE.contentId(), recipe.type());
        assertEquals(8, recipe.time());
        assertEquals(ContentId.of("amberworks:amber_ingot"), recipe.output().id());

        BuildingPrototype prototype = shared.buildings().get(polisher);
        assertEquals("Amber Polisher", prototype.label());
        assertEquals(8, prototype.cost().amount());
        assertEquals(VanillaItems.IRON_PLATE, prototype.cost().item());
        assertEquals(PlacementRule.NEEDS_PASSABLE_TERRAIN, prototype.placementRule());

        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(),
                shared.items(), shared.buildings(), shared.fluids());
        Building building = factory.create(polisher, Direction.RIGHT);
        assertInstanceOf(SimpleCrafter.class, building);

        World world = new World(4, 4, factory);
        SimpleCrafter crafter = (SimpleCrafter) building;
        ItemType ingot = shared.items().get(ContentId.of("amberworks:amber_ingot"));
        assertTrue(crafter.accept(world, ingot));
        for (int i = 0; i < 3; i++) {
            crafter.tick(world, 0, 0);
        }
        assertEquals(ContentId.of("amberworks:amber_polished"), crafter.state().held().id());
    }

    @Test
    void simpleCrafterRejectsUnknownItemIdsAtRegisterTime() {
        GameRegistrationContext shared = new GameRegistrationContext();
        VanillaItems.registerAll(shared.items());
        ModScopedRegistrationContext ctx = new ModScopedRegistrationContext(new ModId("amberworks"), shared);

        ctx.content().item("amber_ore").label("Amber Ore").color(0xC9882A).shape(ItemShape.CIRCLE).register();

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class, () ->
                ctx.content().building("polisher")
                        .label("Broken")
                        .cost("rustorio:iron_plate", 1)
                        .placement("needs_passable_terrain")
                        .texture(VanillaSprites.ASSEMBLER)
                        .simpleCrafter("amber_ore", "does_not_exist", 3)
                        .register());
        assertTrue(thrown.getMessage().contains("does_not_exist")
                        || thrown.getMessage().contains("amberworks:does_not_exist"),
                () -> "expected missing-item message, got: " + thrown.getMessage());
    }

    @Test
    void simpleCrafterCodecRoundTripsBuffers() {
        GameRegistrationContext shared = new GameRegistrationContext();
        VanillaItems.registerAll(shared.items());
        ContentId in = ContentId.of("rustorio:iron_ore");
        ContentId out = ContentId.of("rustorio:iron_plate");
        SimpleCrafterSpec spec = SimpleCrafterSpec.of(in, out, 5);

        BuildingPrototype prototype = new BuildingPrototype(
                ContentId.of("test:crafter"),
                "Crafter",
                new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.ASSEMBLER,
                1, 1, 5, 1, true,
                (self, direction, factory) -> SimpleCrafter.create(self, direction, spec, factory.items()),
                (self, decoded, factory) ->
                        SimpleCrafter.restore(self, (SimpleCrafter.State) decoded, spec, factory.items()),
                SimpleCrafter.CODEC);

        shared.buildings().register(prototype.id(), prototype);
        shared.items().freeze();
        shared.buildings().freeze();
        shared.fluids().freeze();

        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(),
                shared.items(), shared.buildings(), shared.fluids());
        World world = new World(4, 4, factory);
        SimpleCrafter live = (SimpleCrafter) factory.create(prototype.id(), Direction.DOWN);
        live.accept(world, VanillaItems.IRON_ORE);
        live.accept(world, VanillaItems.IRON_ORE);

        Object encoded = SimpleCrafter.CODEC.encode(live.state());
        SimpleCrafter.State decoded = SimpleCrafter.CODEC.decode(encoded, shared.items());
        assertEquals(2, decoded.buffered());
        assertEquals(Direction.DOWN, decoded.direction());

        SimpleCrafter restored = SimpleCrafter.restore(prototype, decoded, spec, shared.items());
        assertEquals(2, restored.state().buffered());
    }

    @Test
    void dslArchetypeBuildingMatchesHandBuiltCostAndArchetypeBehavior() {
        GameRegistrationContext shared = new GameRegistrationContext();
        VanillaItems.registerAll(shared.items());
        ModScopedRegistrationContext ctx = new ModScopedRegistrationContext(new ModId("tune"), shared);

        ContentId id = ctx.content().building("fast_furnace")
                .label("Fast Furnace")
                .cost("rustorio:iron_plate", 12)
                .placement("needs_passable_terrain")
                .texture(VanillaSprites.FURNACE_COLD)
                .archetype(BuildingType.FURNACE)
                .register();

        shared.items().freeze();
        shared.buildings().freeze();

        BuildingPrototype fromDsl = shared.buildings().get(id);
        BuildingPrototype vanilla = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.FURNACE));
        assertEquals(vanilla.behavior(), fromDsl.behavior());
        assertEquals(vanilla.codec(), fromDsl.codec());
        assertEquals(12, fromDsl.cost().amount());
        assertEquals(vanilla.speedTech(), fromDsl.speedTech());
        assertEquals(vanilla.recipeKind(), fromDsl.recipeKind(),
                "DSL archetype must keep the borrowed shared recipe pool, not a private empty one");
        assertEquals(vanilla.fuelItem(), fromDsl.fuelItem(),
                "DSL archetype must keep the borrowed fuel rules (coal for FURNACE)");
    }
}
