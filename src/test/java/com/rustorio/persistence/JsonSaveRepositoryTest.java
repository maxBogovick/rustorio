package com.rustorio.persistence;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.vanilla.VanillaSprites;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.FurnaceState;
import com.rustorio.domain.building.Inserter;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip coverage for {@link JsonSaveRepository} — the riskiest part of the persistence
 * layer: every building kind, an in-progress furnace recipe, and a speed-upgraded building must
 * all come back exactly as they were saved.
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

        assertEquals(VanillaBuildings.idFor(BuildingType.MINER), protoprototypeAt(reloaded, 6, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.CHEST), protoprototypeAt(reloaded, 7, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.FURNACE), protoprototypeAt(reloaded, 8, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.BELT), protoprototypeAt(reloaded, 9, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.SPLITTER), protoprototypeAt(reloaded, 10, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.PRESS), protoprototypeAt(reloaded, 11, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.UNDERGROUND_IN), protoprototypeAt(reloaded, 12, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.UNDERGROUND_OUT), protoprototypeAt(reloaded, 13, 5));
        assertEquals(VanillaBuildings.idFor(BuildingType.LAB), protoprototypeAt(reloaded, 14, 5));
    }

    @Test
    void inProgressFurnaceStateSurvivesARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        world.placeFurnace(0, 0, Direction.RIGHT);
        world.placeChest(1, 0);
        Building furnace = world.peek(0, 0).orElseThrow();
        furnace.accept(world, VanillaItems.IRON_ORE);
        furnace.accept(world, VanillaItems.IRON_ORE); // two ore units buffered, nothing produced yet
        // Fuel too (D-05, DEV_TASKS.md) — one unit per batch below, and its own buffer must
        // survive the round trip exactly like bufferA/bufferB already did.
        furnace.accept(world, VanillaItems.COAL);
        furnace.accept(world, VanillaItems.COAL);

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        Building reloadedFurnace = reloaded.peek(0, 0).orElseThrow();
        Chest chest = (Chest) reloaded.peek(1, 0).orElseThrow();
        int ironTime = RecipeBook.standard().find(BuildingType.FURNACE, VanillaItems.IRON_ORE).orElseThrow().time();

        for (int i = 0; i < ironTime; i++) {
            reloadedFurnace.tick(reloaded, 0, 0);
        }
        assertEquals(1, chest.count(), "the first buffered ore unit must still be there after reload");

        for (int i = 0; i < ironTime; i++) {
            reloadedFurnace.tick(reloaded, 0, 0);
        }
        assertEquals(2, chest.count(), "the second buffered ore unit must still be there too");
    }

    /**
     * (F-03, DEV_TASKS.md) {@code Furnace#selectedRecipe}'s javadoc used to say flatly "Not
     * persisted" — the actual bug this task fixes: before this, save/load silently forgot which
     * recipe the player picked, leaving a press that only accepted an ambiguous item like GEAR
     * before saving right back to refusing it after a reload. This press never has anything
     * committed ({@code active} stays {@code null} the whole test) — only the STANDING preference
     * from {@link Furnace#cycleRecipe} — distinct from {@link #inProgressFurnaceStateSurvivesARoundTrip}'s
     * already-committed batch above.
     */
    @Test
    void selectedRecipePreferenceSurvivesARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(4, 4);
        world.placePress(0, 0, Direction.RIGHT);
        Furnace press = (Furnace) world.peek(0, 0).orElseThrow();
        for (int i = 0; i < 3; i++) { // same three cycles as FurnaceTest's own — lands on ENGINE
            press.cycleRecipe();
        }

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(4, 4);
        assertTrue(repository.load(reloaded).succeeded());

        Furnace reloadedPress = (Furnace) reloaded.peek(0, 0).orElseThrow();
        assertTrue(reloadedPress.accept(reloaded, VanillaItems.GEAR),
                "GEAR alone is ambiguous (ENGINE's first ingredient AND CHASSIS's second) — "
                        + "only survives if the ENGINE preference reloaded with it");
    }

    /**
     * (X-01, DEV_TASKS.md) Splitter's new round-robin flag and Filter/Inserter's whole memento
     * shape are new since {@link com.rustorio.persistence.WorldSnapshot#CURRENT_VERSION} 3 —
     * unlike {@link #savingAndLoadingRestoresEveryBuildingKindAtItsOriginalPosition}'s type-only
     * check, this asserts the actual chosen state (not just the building kind) survives.
     */
    @Test
    void filterAndInserterStateSurviveARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        world.placeFilter(0, 0, Direction.RIGHT);
        Filter filter = (Filter) world.peek(0, 0).orElseThrow();
        filter.cycleFilterItem(); // move off the default so the round trip actually proves something

        world.placeInserter(1, 0, Direction.DOWN);
        Inserter inserter = (Inserter) world.peek(1, 0).orElseThrow();
        inserter.accept(world, VanillaItems.GEAR);

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        Filter reloadedFilter = (Filter) reloaded.peek(0, 0).orElseThrow();
        assertEquals(filter.filterItem(), reloadedFilter.filterItem());

        Inserter reloadedInserter = (Inserter) reloaded.peek(1, 0).orElseThrow();
        assertEquals(Optional.of(VanillaItems.GEAR), reloadedInserter.heldItem());
        assertEquals(Optional.of(Direction.DOWN), reloadedInserter.outputDirection());
    }

    @Test
    void upgradedChestKeepsItsSpeedLevelAfterReload(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        world.placeChest(0, 0);
        assertTrue(new UpgradeSpeedAction(0, 0).apply(world));

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        assertEquals(1, reloaded.peek(0, 0).orElseThrow().speedLevel());
    }

    @Test
    void statsAndResearchSurviveARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(4, 4);
        world.addResearchPoints(costOf(VanillaTechs.FAST_MINING));
        // Unlocking is the player's explicit choice, not automatic (P-02, DEV_TASKS.md) — and it
        // SPENDS the cost, so points end up at 0, not still sitting at FAST_MINING's cost.
        assertTrue(world.tryUnlockTech(VanillaTechs.FAST_MINING));

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(4, 4);
        assertTrue(repository.load(reloaded).succeeded());

        assertTrue(reloaded.research().isUnlocked(VanillaTechs.FAST_MINING));
        assertEquals(0, reloaded.research().points());
    }

    @Test
    void semanticallyBrokenSaveMustNotDestroyTheCurrentWorld(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("save.json");
        SaveRepository repository = new JsonSaveRepository(file);

        World scratch = new World(4, 4);
        scratch.placePress(0, 0, Direction.RIGHT);
        // Commits the press to the GEAR recipe (IRON_PLATE -> GEAR) so the saved FurnaceState
        // carries a non-null recipeOutput to corrupt below.
        scratch.peek(0, 0).orElseThrow().accept(scratch, VanillaItems.IRON_PLATE);
        assertTrue(repository.save(scratch).succeeded());

        // Schema-valid, semantically impossible: no PRESS recipe outputs IRON_PLATE. The
        // Furnace restore constructor throws IllegalStateException for this.
        String corrupted = Files.readString(file).replace("\"rustorio:gear\"", "\"rustorio:iron_plate\"");
        Files.writeString(file, corrupted);

        World world = new World(4, 4);
        world.placeChest(1, 1);

        assertFalse(repository.load(world).succeeded());
        assertTrue(world.peek(1, 1).isPresent(), "a semantically broken save must not wipe the current world");
    }

    /**
     * (Code review finding) {@code OreLayout#restoreDepletion} writes straight into a flat array
     * with no bounds check of its own — an out-of-range index used to throw {@code
     * ArrayIndexOutOfBoundsException} AFTER {@code world.clear()} had already run, and OUTSIDE the
     * try/catch that guards every other "schema-valid but semantically impossible" case, so it
     * propagated straight out of {@code load()} uncaught, with the current world already destroyed.
     * Now validated in phase 1, same as every other case this class already refuses cleanly.
     */
    @Test
    void corruptedOreDepletionIndexFailsCleanlyWithoutDestroyingTheCurrentWorld(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("save.json");
        SaveRepository repository = new JsonSaveRepository(file);

        World scratch = new World(20, 20);
        // A real, valid depletion entry, so the field isn't just empty/absent — (6,5) is the
        // standard map's first iron patch.
        scratch.buildingFactory().oreLayout().extract(6, 5);
        assertTrue(repository.save(scratch).succeeded());

        // No 256x256 map could ever produce this flat index — same regex technique already used by
        // aSaveWithoutAnOreLayoutFieldIsTreatedAsAnUnknownMapAndStillLoads below.
        String corrupted = Files.readString(file).replaceFirst("\"oreDepletion\"\\s*:\\s*\\{[^}]*}",
                "\"oreDepletion\" : {\n    \"999999999\" : 1\n  }");
        Files.writeString(file, corrupted);

        World world = new World(4, 4);
        world.placeChest(1, 1);

        assertFalse(repository.load(world).succeeded());
        assertTrue(world.peek(1, 1).isPresent(), "a corrupted depletion index must not wipe the current world");
    }

    /**
     * (Code review finding, Баг 8) {@code World#restoreBuilding} never checks bounds itself — it
     * trusts placement rules were already satisfied when a building was first built. A save whose
     * building doesn't fit the world being loaded into (here: the same standard 256x256 ore map,
     * but a smaller playable grid than the one the save was made on) used to silently reserve
     * out-of-range {@code Coord}s in {@code World#occupancy} with no exception and no signal at
     * all. Now validated in phase 1, against the actual rebuilt footprint.
     */
    @Test
    void buildingThatDoesNotFitTheCurrentWorldFailsCleanlyWithoutDestroyingIt(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));

        World big = new World(20, 20);
        assertTrue(big.place(BuildingType.ASSEMBLER, 18, 18, Direction.RIGHT), "fits fine in a 20x20 world");
        assertTrue(repository.save(big).succeeded());

        World small = new World(4, 4); // same standard 256x256 ore map, much smaller playable grid
        small.placeChest(1, 1);

        assertFalse(repository.load(small).succeeded());
        assertTrue(small.peek(1, 1).isPresent(), "a building that doesn't fit the current world must not wipe it");
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

    /** (D-07, DEV_TASKS.md) The card's own acceptance criterion: an unsupported version must fail cleanly, not guess. */
    @Test
    void loadingASaveWithAnUnsupportedVersionFails(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("save.json");
        SaveRepository repository = new JsonSaveRepository(file);

        World scratch = new World(4, 4);
        scratch.placeChest(0, 0);
        assertTrue(repository.save(scratch).succeeded());

        String corrupted = Files.readString(file).replaceFirst("\"version\"\\s*:\\s*\\d+", "\"version\" : 999");
        Files.writeString(file, corrupted);

        World world = new World(4, 4);
        world.placeChest(1, 1);

        SaveResult result = repository.load(world);
        assertFalse(result.succeeded());
        assertTrue(world.peek(1, 1).isPresent(), "an unsupported version must not touch the current world");
    }

    /**
     * (N3, NEW_BUGS_PROGRESS.md) The world's clock wasn't in any snapshot: {@code World.tickCount}
     * went back to 0 on every load, so production statistics — which are timestamped in ticks and
     * ARE persisted — came back attached to a clock that had restarted underneath them, and
     * hand-mining cooldowns were measured against it too.
     */
    @Test
    void theWorldClockSurvivesARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        for (int i = 0; i < 25; i++) {
            world.tick();
        }
        assertEquals(25, world.currentTick());

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        assertEquals(25, reloaded.currentTick(), "the clock must resume where the save left it, not at zero");
    }

    /**
     * (N11, NEW_BUGS_PROGRESS.md) {@code PatchOreLayout.id()} reported {@code width}/{@code height}
     * as 0 while building a 256x256 map — the identifier the compatibility check compares is the one
     * place those dimensions have to be honest.
     */
    @Test
    void theStandardMapIdentifiesItselfWithItsRealDimensions() {
        var id = new World(4, 4).buildingFactory().oreLayout().id();

        assertEquals("patch", id.kind());
        assertEquals(PatchOreLayout.STANDARD_WIDTH, id.width());
        assertEquals(PatchOreLayout.STANDARD_HEIGHT, id.height());
    }

    /** (D-07, DEV_TASKS.md) The player's inventory (D-03) wasn't in any snapshot before this task. */
    @Test
    void playerInventorySurvivesARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(6, 6);
        assertTrue(world.trySpendBuildingCost(BuildingType.CHEST)); // moves it off the untouched starting amount
        int platesBeforeSave = world.inventory().amount(VanillaItems.IRON_PLATE);

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(6, 6);
        assertTrue(repository.load(reloaded).succeeded());

        assertEquals(platesBeforeSave, reloaded.inventory().amount(VanillaItems.IRON_PLATE));
    }

    /** (D-07, DEV_TASKS.md) Ore depletion (D-04) wasn't in any snapshot before this task. */
    @Test
    void oreDepletionSurvivesARoundTrip(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(20, 20);
        world.placeChest(0, 0); // keeps the world non-trivial; the depletion itself lives on the ore layout, not a building

        // (6, 5) is the center of the standard map's first iron patch — deplete it well into its
        // thin tail (OreDepletion.RICHNESS + a few tail calls) directly on the layout, the same
        // path Miner.tick reaches through extract().
        var oreLayout = world.buildingFactory().oreLayout();
        for (int i = 0; i < 6000 + 25; i++) {
            oreLayout.extract(6, 5);
        }
        var depletionBeforeSave = oreLayout.depletionSnapshot();
        assertFalse(depletionBeforeSave.isEmpty(), "the extracted cell must actually show up in the snapshot");

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(20, 20);
        assertTrue(repository.load(reloaded).succeeded());

        assertEquals(depletionBeforeSave, reloaded.buildingFactory().oreLayout().depletionSnapshot());
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

    /**
     * (E6-04, ADR-4) A {@code prototypeId} absent from the loading world's own registry — its mod
     * was removed since the save was written — must not fail the whole load: only that one cell
     * stays empty, every other building loads normally, and the loss is reported, not silent.
     */
    @Test
    void loadingASaveThatNamesAMissingModdedPrototypeSkipsItAndReportsTheLoss(@TempDir Path dir) {
        ContentId moddedId = ContentId.of("examplemod:steel_press");
        BuildingFactory moddedFactory = factoryWithModdedPress(moddedId);

        World world = new World(4, 4, moddedFactory);
        world.restoreBuilding(0, 0, moddedFactory.create(moddedId, Direction.RIGHT));
        world.placeChest(1, 0);

        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        assertTrue(repository.save(world).succeeded());

        // Reload into a world whose factory only knows the vanilla set — the mod that provided
        // "examplemod:steel_press" is gone.
        World reloaded = new World(4, 4);
        SaveResult result = repository.load(reloaded);

        assertTrue(result.succeeded(), "a missing prototype must not fail the whole load");
        SaveResult.PartialSuccess partial = assertInstanceOf(SaveResult.PartialSuccess.class, result);
        assertEquals(List.of(moddedId), partial.missingPrototypeIds());
        assertEquals(1, partial.buildingsSkipped());

        assertTrue(reloaded.peek(0, 0).isEmpty(), "the cell for the missing prototype must stay empty");
        assertEquals(VanillaBuildings.idFor(BuildingType.CHEST), reloaded.peek(1, 0).orElseThrow().prototypeId(),
                "every other building must still load normally");
    }

    /**
     * (E6-05, ADR-4) A prototype renamed since the save was written — the loading registry no
     * longer has {@code oldId} at all, only {@code newId} — resolves under the NEW prototype via a
     * single {@code oldId -> newId} table lookup, instead of being reported missing (E6-04) just
     * because the stale name is gone.
     */
    @Test
    void loadingASaveThatNamesARenamedPrototypeResolvesUnderTheNewOne(@TempDir Path dir) {
        ContentId oldId = ContentId.of("mymod:crusher");
        ContentId newId = ContentId.of("mymod:grinder");

        World world = new World(4, 4, factoryWithModdedPress(oldId));
        world.restoreBuilding(0, 0, world.buildingFactory().create(oldId, Direction.RIGHT));
        SaveRepository writer = new JsonSaveRepository(dir.resolve("save.json"));
        assertTrue(writer.save(world).succeeded());

        // The loading factory only knows the NEW id — "mymod:crusher" isn't registered at all.
        World reloaded = new World(4, 4, factoryWithModdedPress(newId));
        SaveRepository reader = new JsonSaveRepository(
                dir.resolve("save.json"), VanillaItems.frozen(), Map.of(oldId, newId));
        SaveResult result = reader.load(reloaded);

        assertTrue(result.succeeded(), "a renamed prototype must not be reported as missing");
        Building restored = reloaded.peek(0, 0).orElseThrow();
        assertEquals(newId, restored.prototypeId(),
                "must resolve under the renamed prototype's own id, not the stale saved one");
    }

    /**
     * (E6-05, ADR-4) The other half of the card's own acceptance criterion: a row that ALREADY
     * names the current prototype directly must be completely unaffected by an unrelated rename
     * rule for some other stale id sitting in the same table — proves the lookup targets exactly
     * one row's own {@code prototypeId}, not something that could misfire across rows or reapply
     * itself, rather than relying on this being accidentally true.
     */
    @Test
    void aSaveThatAlreadyNamesTheCurrentPrototypeIsUnaffectedByAnUnrelatedRenameRule(@TempDir Path dir) {
        ContentId currentId = ContentId.of("mymod:grinder");
        BuildingFactory factory = factoryWithModdedPress(currentId);

        World world = new World(4, 4, factory);
        world.restoreBuilding(0, 0, factory.create(currentId, Direction.RIGHT));
        SaveRepository writer = new JsonSaveRepository(dir.resolve("save.json"));
        assertTrue(writer.save(world).succeeded());

        Map<ContentId, ContentId> staleRenameForADifferentId =
                Map.of(ContentId.of("mymod:crusher"), currentId);
        World reloaded = new World(4, 4, factory);
        SaveRepository reader = new JsonSaveRepository(
                dir.resolve("save.json"), VanillaItems.frozen(), staleRenameForADifferentId);
        SaveResult result = reader.load(reloaded);

        assertTrue(result.succeeded());
        assertEquals(currentId, reloaded.peek(0, 0).orElseThrow().prototypeId(),
                "an unrelated rename rule must not touch a row that already names the current id");
    }

    /** A "steel press"-shaped modded prototype (bigger buffer, twice the speed) registered under {@code id}, next to the 12 vanilla ones. */
    private static BuildingFactory factoryWithModdedPress(ContentId id) {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        prototypes.register(id, new BuildingPrototype(
                id,
                id.path(),
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.FURNACE_COLD,
                10, 2, true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(BuildingType.PRESS, state, factory.recipeBook(), self);
                },
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec()));
        prototypes.freeze();
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);
    }

    private static ContentId protoprototypeAt(World world, int x, int y) {
        return world.peek(x, y).orElseThrow().prototypeId();
    }

    /** A vanilla technology's price, read from the registry the game itself researches through. */
    private static int costOf(com.rustorio.api.content.ContentId tech) {
        return VanillaTechs.frozen().get(tech).cost();
    }
}
