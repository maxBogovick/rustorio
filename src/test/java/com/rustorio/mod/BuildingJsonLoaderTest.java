package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioral proof for {@link BuildingJsonLoader} — a JSON-configured prototype must actually
 * behave differently at the {@link Furnace#tick} level, not just carry the right numbers, the same
 * standard {@code BuildingFactoryTest.createBuildsAPrototypeWithNoCorrespondingBuildingType} already
 * holds a Java-literal modded prototype to.
 */
class BuildingJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void jsonConfiguredBuildingReusesTheNamedArchetypeAndAppliesItsOwnTuning() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("steel_press.json", """
                { "path": "steel_press", "label": "Steel Press", "archetype": "PRESS", "kind": "PRESS",
                  "cost": { "item": "rustorio:iron_plate", "amount": 20 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold",
                  "bufferMax": 10, "speedMultiplier": 2, "acceptsSpeedEffects": true }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        ContentId id = ContentId.of("testmod:steel_press");
        BuildingPrototype prototype = context.buildings().peek(id).orElseThrow();
        assertEquals(10, prototype.bufferMax());
        assertEquals(2, prototype.speedMultiplier());
        assertEquals(20, prototype.cost().amount());

        context.items().freeze();
        Registry<BuildingPrototype> buildings = new Registry<>();
        VanillaBuildings.registerAll(buildings);
        buildings.register(id, prototype);
        buildings.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), context.items(), buildings);

        Building built = factory.create(id, Direction.RIGHT);
        Furnace press = assertInstanceOf(Furnace.class, built);

        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));

        int gearTime = RecipeBook.standard().findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();
        int fastTime = Math.max(1, gearTime / 2); // this prototype's own speedMultiplier: 2
        for (int i = 0; i < fastTime - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count(), "must not finish before the JSON prototype's sped-up time");
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.amount(VanillaItems.GEAR),
                "cooked in half the vanilla PRESS's time via the JSON prototype's own speedMultiplier");
    }

    @Test
    void explicitFootprintInJsonDrivesTheRealConstructedBuildingNotJustTheArchetypesOwn() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("big_furnace.json", """
                { "path": "big_furnace", "label": "Big Furnace", "archetype": "FURNACE",
                  "cost": { "item": "rustorio:iron_plate", "amount": 5 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold",
                  "footprintWidth": 3, "footprintHeight": 2, "bufferMax": 5, "speedMultiplier": 1,
                  "acceptsSpeedEffects": true }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        ContentId id = ContentId.of("testmod:big_furnace");
        BuildingPrototype prototype = context.buildings().peek(id).orElseThrow();
        assertEquals(3, prototype.footprintWidth());
        assertEquals(2, prototype.footprintHeight());

        context.items().freeze();
        Registry<BuildingPrototype> buildings = new Registry<>();
        VanillaBuildings.registerAll(buildings);
        buildings.register(id, prototype);
        buildings.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), context.items(), buildings);

        Building built = factory.create(id, Direction.RIGHT);
        assertEquals(3, built.footprintWidth(), "the REAL constructed building must read footprint from ITS OWN prototype, not the borrowed archetype's");
        assertEquals(2, built.footprintHeight());
    }

    @Test
    void unknownArchetypeNamesTheAllowedValues() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "TELEPORTER",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "ALWAYS", "texture": "rustorio:miner" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("MINER"));
    }

    @Test
    void unknownPlacementNamesTheAllowedValues() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "CHEST",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "FLOATING", "texture": "rustorio:chest" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("NEEDS_PASSABLE_TERRAIN"));
    }

    @Test
    void unresolvedCostItemFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "CHEST",
                  "cost": { "item": "rustorio:unobtainium", "amount": 1 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:chest" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("rustorio:unobtainium"));
    }

    @Test
    void missingCostObjectFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "CHEST", "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:chest" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("cost"));
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
