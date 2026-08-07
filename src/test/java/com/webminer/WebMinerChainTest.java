package com.webminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaTechs;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.WorldServices;
import com.rustorio.domain.world.World;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The whole chain this mod exists to make — {@code WebMiner -> Monitor -> Interpreter -> Belt ->
 * Chest} — with a response that resolves immediately, ticked until the fetched item has travelled
 * all the way through.
 *
 * <p>This is the gap every other test here left open, and the gap is structural rather than
 * accidental. {@code WebMinerAsyncFetchTest} delivers into a plain chest, so it never exercises a
 * Monitor or an Interpreter receiving anything. {@code DevSceneTest} builds this exact chain but
 * ticks 2000 times against a REAL network call: a fetch that takes a few hundred milliseconds has
 * not resolved by the time a 2000-iteration loop finishes in a few, so the delivery half never
 * runs there either. Both were green while the path a player watches within seconds of pressing
 * {@code --dev} was covered by nothing.
 */
class WebMinerChainTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path WEBMINER_MOD_DIR = Path.of("resources", "mods", "webminer");
    private static final String BODY = "{\"current_user_url\":\"https://api.github.com/user\"}";

    /** Resolves on the calling thread, before {@code submit} even returns — the fastest possible response, so the delivery half runs within a handful of ticks instead of never. */
    private static World chainWorld() {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WEBMINER_MOD_DIR));
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        WebMinerMod.registerAll(prototypes, content.items());
        prototypes.freeze();

        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(),
                content.items(), prototypes);
        WorldServices services = WorldServices.builder()
                .with(FetchService.KEY, () -> new FetchService((url, onComplete) ->
                        onComplete.accept(new FetchResult(FetchOutcome.SUCCESS, BODY))))
                .build();
        World world = new World(8, 8, factory, VanillaTechs.frozen(), services);
        world.restoreBuilding(0, 0, factory.create(WebMinerMod.WEB_MINER_ID, Direction.RIGHT));
        world.restoreBuilding(1, 0, factory.create(WebMinerMod.MONITOR_ID, Direction.RIGHT));
        world.restoreBuilding(2, 0, factory.create(WebMinerMod.INTERPRETER_ID, Direction.RIGHT));
        world.placeBelt(3, 0, Direction.RIGHT);
        world.placeChest(4, 0);
        return world;
    }

    @Test
    void aFetchedItemTravelsTheWholeChainWithoutTheTickLoopThrowing() {
        World world = chainWorld();

        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        Chest chest = (Chest) world.peek(4, 0).orElseThrow();
        assertEquals(1, chest.count(),
                "the fetched item must reach the end of the chain — one fetch, one item, "
                        + "and the rate limiter stops a second one for a whole minute");
    }

    /** The read side, at the moment it actually has something to read — the monitor shows the body the miner behind it fetched. */
    @Test
    void theMonitorShowsTheBodyFetchedByTheMinerBehindIt() {
        World world = chainWorld();
        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        List<String> details = ((InspectableBuilding) world.peek(1, 0).orElseThrow())
                .inspectionDetails(world, 1, 0);

        assertEquals(List.of(BODY), details, "the monitor reads the cell behind it, which is the miner");
    }

    /**
     * The interpreter pulls a named field out of the body — with the interpreter placed DIRECTLY
     * behind the miner, because one cell back is exactly as far as either reader looks.
     *
     * <p>That is worth stating plainly, because it is easy to get wrong and the failure is silent:
     * put a monitor between the miner and the interpreter and the interpreter reads the MONITOR,
     * which has fetched nothing, so it shows "no response seen" forever. Both readers answer about
     * the single cell behind them; they do not chain.
     */
    @Test
    void theInterpreterExtractsItsConfiguredFieldFromTheBodyOneCellBehindIt() {
        World world = chainWorld();
        // (5, 1): a second miner with the interpreter immediately after it, clear of the row above.
        world.restoreBuilding(0, 1, world.buildingFactory().create(WebMinerMod.WEB_MINER_ID, Direction.RIGHT));
        world.restoreBuilding(1, 1, world.buildingFactory().create(WebMinerMod.INTERPRETER_ID, Direction.RIGHT));
        EditableBuilding interpreter = (EditableBuilding) world.peek(1, 1).orElseThrow();
        interpreter.applyEdits(List.of("current_user_url"));

        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        List<String> details = ((InspectableBuilding) interpreter).inspectionDetails(world, 1, 1);
        assertTrue(details.stream().anyMatch(line -> line.contains("current_user_url")
                        && line.contains("https://api.github.com/user")),
                "the configured field must be extracted from the body fetched one cell back: " + details);
    }
}
