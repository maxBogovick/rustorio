package com.rustorio.persistence;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Tech;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip coverage for {@link JsonSaveRepository} — the riskiest part of the persistence
 * layer: every building kind, an in-progress furnace recipe, and an upgraded belt must all come
 * back exactly as they were saved.
 */
class JsonSaveRepositoryTest {

    @Test
    void loadingAMissingFileFailsWithoutTouchingTheWorld(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("does-not-exist.json"));
        World world = new World(4, 4);
        world.placeChest(0, 0);

        assertFalse(repository.load(world).succeeded());
        assertTrue(world.peek(0, 0).isPresent(), "a failed load must not clear the current world");
    }

    @Test
    void savingAndLoadingRestoresEveryBuildingKindAtItsOriginalPosition(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(20, 20);

        world.placeMiner(6, 5); // (6, 5) is the center of the standard map's first iron patch
        world.placeChest(7, 5);
        world.placeFurnace(8, 5, Direction.RIGHT);
        world.placeBelt(9, 5, Direction.RIGHT);
        world.placeSplitter(10, 5, Direction.DOWN);
        world.placePress(11, 5, Direction.UP);
        world.placeUndergroundIn(12, 5, Direction.LEFT);
        world.placeUndergroundOut(13, 5, Direction.LEFT);
        world.placeLab(14, 5);

        assertTrue(repository.save(world).succeeded());

        World reloaded = new World(20, 20);
        assertTrue(repository.load(reloaded).succeeded());

        assertEquals(BuildingType.MINER, typeAt(reloaded, 6, 5));
        assertEquals(BuildingType.CHEST, typeAt(reloaded, 7, 5));
        assertEquals(BuildingType.FURNACE, typeAt(reloaded, 8, 5));
        assertEquals(BuildingType.BELT, typeAt(reloaded, 9, 5));
        assertEquals(BuildingType.SPLITTER, typeAt(reloaded, 10, 5));
        assertEquals(BuildingType.PRESS, typeAt(reloaded, 11, 5));
        assertEquals(BuildingType.UNDERGROUND_IN, typeAt(reloaded, 12, 5));
        assertEquals(BuildingType.UNDERGROUND_OUT, typeAt(reloaded, 13, 5));
        assertEquals(BuildingType.LAB, typeAt(reloaded, 14, 5));
    }

    @Test
    void inProgressFurnaceStateSurvivesARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        world.placeFurnace(0, 0, Direction.RIGHT);
        world.placeChest(1, 0);
        Building furnace = world.peek(0, 0).orElseThrow();
        furnace.accept(world, Item.IRON_ORE);
        furnace.accept(world, Item.IRON_ORE); // two ore units buffered, nothing produced yet

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        Building reloadedFurnace = reloaded.peek(0, 0).orElseThrow();
        Chest chest = (Chest) reloaded.peek(1, 0).orElseThrow();
        int ironTime = RecipeBook.standard().find(BuildingType.FURNACE, Item.IRON_ORE).orElseThrow().time();

        for (int i = 0; i < ironTime; i++) {
            reloadedFurnace.tick(reloaded, 0, 0);
        }
        assertEquals(1, chest.count(), "the first buffered ore unit must still be there after reload");

        for (int i = 0; i < ironTime; i++) {
            reloadedFurnace.tick(reloaded, 0, 0);
        }
        assertEquals(2, chest.count(), "the second buffered ore unit must still be there too");
    }

    @Test
    void upgradedBeltKeepsItsSpeedModuleLayerAfterReload(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.restoreBuilding(0, 0, new SpeedModule(world.removeBuilding(0, 0).orElseThrow()));

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        assertEquals(1, reloaded.peek(0, 0).orElseThrow().speedLevel());
    }

    @Test
    void statsAndResearchSurviveARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(4, 4);
        world.addResearchPoints(Tech.FAST_MINING.cost());

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(4, 4);
        assertTrue(repository.load(reloaded).succeeded());

        assertTrue(reloaded.research().isUnlocked(Tech.FAST_MINING));
        assertEquals(Tech.FAST_MINING.cost(), reloaded.research().points());
    }

    @Test
    void semanticallyBrokenSaveMustNotDestroyTheCurrentWorld(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("save.json");
        SaveRepository repository = new JsonSaveRepository(file);

        World scratch = new World(4, 4);
        scratch.placePress(0, 0, Direction.RIGHT);
        // Commits the press to the GEAR recipe (IRON_PLATE -> GEAR) so the saved FurnaceState
        // carries a non-null recipeOutput to corrupt below.
        scratch.peek(0, 0).orElseThrow().accept(scratch, Item.IRON_PLATE);
        assertTrue(repository.save(scratch).succeeded());

        // Schema-valid, semantically impossible: no PRESS recipe outputs IRON_PLATE. The
        // Furnace restore constructor throws IllegalStateException for this.
        String corrupted = Files.readString(file).replace("\"GEAR\"", "\"IRON_PLATE\"");
        Files.writeString(file, corrupted);

        World world = new World(4, 4);
        world.placeChest(1, 1);

        assertFalse(repository.load(world).succeeded());
        assertTrue(world.peek(1, 1).isPresent(), "a semantically broken save must not wipe the current world");
    }

    @Test
    void saveFromASeededMapIsNotSilentlyLoadedIntoTheStandardMap(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World seeded = new World(20, 20,
                new BuildingFactory(new RandomOreLayout(42, 20, 20), RecipeBook.standard()));
        assertTrue(repository.save(seeded).succeeded());

        World standard = new World(20, 20); // BuildingFactory.standard() -> PatchOreLayout, a different map
        standard.placeChest(0, 0);

        assertFalse(repository.load(standard).succeeded());
        assertTrue(standard.peek(0, 0).isPresent(), "a map mismatch must not touch the current world");
    }

    @Test
    void aSaveWithoutAnOreLayoutFieldIsTreatedAsAnUnknownMapAndStillLoads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("save.json");
        SaveRepository repository = new JsonSaveRepository(file);
        World world = new World(4, 4);
        world.placeChest(0, 0);
        assertTrue(repository.save(world).succeeded());

        // Simulate a save written before P2-01 introduced the field: drop it entirely.
        String withoutOreLayout = Files.readString(file).replaceAll(",?\\s*\"oreLayout\"\\s*:\\s*\\{[^}]*}", "");
        Files.writeString(file, withoutOreLayout);

        World reloaded = new World(4, 4);
        assertTrue(repository.load(reloaded).succeeded());
        assertTrue(reloaded.peek(0, 0).isPresent());
    }

    private static BuildingType typeAt(World world, int x, int y) {
        return world.peek(x, y).orElseThrow().type();
    }
}
