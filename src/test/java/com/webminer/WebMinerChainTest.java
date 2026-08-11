package com.webminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingImage;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.ViewableBuilding;
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
        return chainWorld((url, onComplete) -> onComplete.accept(new FetchResult(FetchOutcome.SUCCESS, BODY)));
    }

    private static World chainWorld(FetchExecutor executor) {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WEBMINER_MOD_DIR));
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        WebMinerMod.registerAll(prototypes, content.items());
        prototypes.freeze();

        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(),
                content.items(), prototypes);
        WorldServices services = WorldServices.builder()
                .with(FetchService.KEY, () -> new FetchService(executor))
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

    /**
     * The read side, at the moment it actually has something to read — the monitor describes the
     * response the miner behind it fetched.
     *
     * <p>It used to assert the body verbatim, {@code List.of(BODY)}, and that is what changed:
     * quoting a body from the top shows a JSON API's real fields by luck and shows an HTML page's
     * {@code <meta charset>} and favicon links by the same luck. What this pins now is that the
     * monitor still reads the cell BEHIND it — the part that was always the point — and that what
     * it reports is about the response rather than a slice of it. {@link ResponsePreviewTest}
     * covers the shape of those lines for each kind of body.
     */
    @Test
    void theMonitorSummarisesTheResponseFetchedByTheMinerBehindIt() {
        World world = chainWorld();
        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        List<String> details = ((InspectableBuilding) world.peek(1, 0).orElseThrow())
                .inspectionDetails(world, 1, 0);

        assertTrue(details.getFirst().startsWith("Response: JSON"),
                "the monitor reads the cell behind it, which is the miner: " + details);
        assertTrue(details.contains("  current_user_url: https://api.github.com/user"),
                "the field the fetched body actually carries must be on the panel: " + details);
    }

    /**
     * A miner whose fetches all fail leaves its monitor saying so, end to end through the real
     * {@link FetchService}.
     *
     * <p>The service used to remember successful responses only, so a miner pointed at a URL that
     * never works produced no record at all and the monitor fell through to "no response seen yet"
     * — the sentence a brand-new miner shows. A player mistyping a URL got no signal from the game
     * that anything was wrong, forever.
     */
    @Test
    void aMonitorBehindAMinerThatOnlyEverFailsReportsTheFailure() {
        World world = chainWorld((url, onComplete) ->
                onComplete.accept(new FetchResult(FetchOutcome.ERROR, null, 404, null, 0)));
        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        List<String> details = ((InspectableBuilding) world.peek(1, 0).orElseThrow())
                .inspectionDetails(world, 1, 0);

        assertEquals("Fetch failed: HTTP 404", details.getFirst(),
                "a failure has to reach the panel, not just the belt: " + details);
    }

    /**
     * A monitor hands over the page its miner fetched as pixels — the door {@code ViewableBuilding}
     * opens, checked end to end rather than only at the renderer that draws them.
     *
     * <p>The fixture renders the page the way {@code HttpFetchExecutor} does, because that is the
     * only place it can be rendered: the body a monitor can still see has been capped to a few
     * kilobytes by the time anyone asks. What this pins is the handover — that a building with a
     * page produces an image of the right size, and that a building without one produces nothing
     * rather than an empty picture.
     */
    @Test
    void aMonitorHandsOverTheFetchedPageAsPixels() {
        String page = "<!doctype html><html><head><title>Cabinet</title></head>"
                + "<body><h1>Cabinet</h1><p>One of a kind miniature dolls.</p></body></html>";
        World world = chainWorld((url, onComplete) -> onComplete.accept(new FetchResult(FetchOutcome.SUCCESS,
                page, 200, "text/html", page.length(), ResponsePreview.digestHtml(page),
                PageRenderer.render(page, "https://example.invalid/"))));
        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        BuildingImage image = ((ViewableBuilding) world.peek(1, 0).orElseThrow())
                .image(world, 1, 0).orElseThrow();

        assertTrue(image.width() > 0 && image.height() > 0, "a drawn page has a size: " + image.width() + "x" + image.height());
        assertEquals(image.width() * image.height(), image.argb().length, "every pixel of it must actually be there");
    }

    /** No page fetched means no picture — an ordinary state, and it must not become a blank window the player has to close. */
    @Test
    void aMonitorWithNoPageBehindItOffersNoPictureAtAll() {
        World world = chainWorld(); // the JSON fixture: a real response, but not a page
        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        assertTrue(((ViewableBuilding) world.peek(1, 0).orElseThrow()).image(world, 1, 0).isEmpty(),
                "a JSON body is not a page, and pretending otherwise would open an empty viewer");
    }

    /** The miner itself names its target. It is the one setting a player types in, and until now the only way to see it back was to open the editor again. */
    @Test
    void theMinerShowsTheUrlItIsPointedAt() {
        World world = chainWorld();

        List<String> details = ((InspectableBuilding) world.peek(0, 0).orElseThrow())
                .inspectionDetails(world, 0, 0);

        assertTrue(details.getFirst().startsWith("URL: https://"), "the target belongs on the panel: " + details);
        assertTrue(details.stream().anyMatch(line -> line.startsWith("Fetches every ")),
                "how often it goes out is the other half of what a miner is doing: " + details);
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
