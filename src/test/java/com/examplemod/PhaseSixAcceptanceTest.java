package com.examplemod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.FurnaceState;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.JsonSaveRepository;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6's own acceptance capstone, word for word ({@code ENGINE_PLAN.md}'s Phase 6 criterion):
 * "Сейв с модом → мод удалён → игра открывается с внятным отчётом. Переименование
 * {@code mymod:crusher} → {@code mymod:grinder} проходит миграцией." Both scenarios go through a
 * real {@link JsonSaveRepository}, not a bypass — everything each one needs was already proven
 * piecemeal in E6-01…E6-05 ({@code CodecTest}, {@code VanillaBuildingsCodecTest},
 * {@code JsonSaveRepositoryTest}, {@code StateMigrationTest}); this test just assembles the two
 * scenarios the phase itself names, the way {@link PhaseFiveAcceptanceTest} did for Phase 5.
 *
 * <p>{@code mymod:crusher} reuses {@link Furnace} outright — no new Java class needed, the same
 * "narrow path" E5-08's building half (a "steel press") and every Phase 6 codec test already used.
 * The point of this test isn't a novel archetype; it's proving the SAVE/LOAD half of moddability
 * end to end, in the two shapes the phase's own criterion names.
 */
class PhaseSixAcceptanceTest {

    private static final ContentId CRUSHER_ID = ContentId.of("mymod:crusher");
    private static final ContentId GRINDER_ID = ContentId.of("mymod:grinder");

    /** A "crusher"/"grinder" — bigger buffer, twice the speed, reusing {@link Furnace}'s {@code PRESS} logic — registered under {@code id}, next to the 12 vanilla prototypes. */
    private static BuildingFactory factoryWithModdedPress(ContentId id) {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        prototypes.register(id, new BuildingPrototype(
                id,
                id.path(),
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.FURNACE_COLD,
                1, 1,
                10, // buffer — double the vanilla PRESS's 5
                2, // speed multiplier — twice as fast
                true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(BuildingType.PRESS, state, factory.recipeBook(), self);
                },
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec(),
                // Shares the vanilla PRESS recipe pool (this test feeds IRON_PLATE and expects the
                // real vanilla GEAR recipe) instead of the private-pool default.
                VanillaBuildings.idFor(BuildingType.PRESS), null));
        prototypes.freeze();
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);
    }

    /**
     * Scenario 1 (E6-04): a save naming {@code mymod:crusher}, loaded into a world whose factory
     * never heard of it — the mod was removed. The game must open with an honest report, not crash,
     * and everything else in the save must still be there.
     */
    @Test
    void modRemovedLeavesAnHonestReportInsteadOfCrashing(@TempDir Path dir) {
        BuildingFactory moddedFactory = factoryWithModdedPress(CRUSHER_ID);
        World world = new World(4, 4, moddedFactory);
        world.restoreBuilding(0, 0, moddedFactory.create(CRUSHER_ID, Direction.RIGHT));
        world.placeChest(1, 0);

        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        assertTrue(repository.save(world).succeeded());

        // The mod is gone: a fresh vanilla-only factory, nothing registered under CRUSHER_ID.
        World reloaded = new World(4, 4);
        SaveResult result = repository.load(reloaded);

        assertTrue(result.succeeded(), "a missing mod must open the save with a report, not fail it");
        SaveResult.PartialSuccess report = assertInstanceOf(SaveResult.PartialSuccess.class, result);
        assertEquals(List.of(CRUSHER_ID), report.missingPrototypeIds(), "the report must name the exact lost prototype");
        assertEquals(1, report.buildingsSkipped());

        assertTrue(reloaded.peek(0, 0).isEmpty(), "the crusher's own cell stays empty");
        assertEquals(VanillaBuildings.idFor(BuildingType.CHEST), reloaded.peek(1, 0).orElseThrow().prototypeId(),
                "the rest of the world must still be there");
    }

    /**
     * Scenario 2 (E6-05): the same {@code mymod:crusher} save, but the mod renamed its prototype to
     * {@code mymod:grinder} instead of disappearing. Migration must resolve the building under the
     * NEW id and it must keep behaving exactly as before the rename — buffer and cook speed still
     * driven by the (now renamed) modded prototype's own data, not a silent fallback to any default.
     */
    @Test
    void modRenamedMigratesTheSavedPrototypeToItsNewIdAndKeepsBehaving(@TempDir Path dir) {
        BuildingFactory oldFactory = factoryWithModdedPress(CRUSHER_ID);
        World world = new World(4, 4, oldFactory);
        world.restoreBuilding(0, 0, oldFactory.create(CRUSHER_ID, Direction.RIGHT));
        world.placeChest(1, 0);

        SaveRepository writer = new JsonSaveRepository(dir.resolve("save.json"));
        assertTrue(writer.save(world).succeeded());

        // The mod renamed its prototype: the loading registry only knows "mymod:grinder".
        BuildingFactory newFactory = factoryWithModdedPress(GRINDER_ID);
        World reloaded = new World(4, 4, newFactory);
        SaveRepository reader = new JsonSaveRepository(
                dir.resolve("save.json"), VanillaItems.frozen(), Map.of(CRUSHER_ID, GRINDER_ID));
        SaveResult result = reader.load(reloaded);

        assertTrue(result.succeeded());
        Building restored = reloaded.peek(0, 0).orElseThrow();
        Furnace press = assertInstanceOf(Furnace.class, restored);
        assertEquals(GRINDER_ID, press.prototypeId(), "must be resolved under the renamed prototype, not the stale saved id");

        // Behavioral proof, not just identity: still twice the vanilla PRESS's speed and double its
        // buffer — the SAME numbers the crusher had, now served by the grinder prototype.
        Chest chest = (Chest) reloaded.peek(1, 0).orElseThrow();
        assertTrue(press.accept(reloaded, VanillaItems.IRON_PLATE));
        int gearTime = RecipeBook.standard().findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();
        int fastTime = Math.max(1, gearTime / 2);
        for (int i = 0; i < fastTime; i++) {
            press.tick(reloaded, 0, 0);
        }
        assertEquals(1, chest.amount(VanillaItems.GEAR),
                "renamed prototype's own speedMultiplier must still drive cooking, exactly as before the rename");
    }
}
