package com.graphics.screen;

import com.graphics.GfxConfig;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DevScene}: every recipe chain it builds must actually RUN, not just place without
 * throwing — a coordinate collision between two chains (two chests planned for the same cell)
 * would silently fail one placement and merge two supply chests into one, which compiles and
 * doesn't crash, but quietly breaks the "every recipe genuinely produces" promise. This runs the
 * exact same {@link World#tick()} loop the real game does and checks every chain's own output
 * chest actually accumulated the item it's supposed to make.
 *
 * <p>Goes through {@link GameBootstrap#createWorld} on real loaded mods, which is exactly what the
 * running game does — {@link DevScene#build} places the {@code webminer} mod's own buildings too,
 * and they resolve because that mod is loaded here, not because this fixture merged anything in by
 * hand (it used to have to). The web miner's own chest is deliberately not asserted on: that would
 * be a real outbound HTTP call, which has no place in this suite — see {@code
 * WebMinerAsyncFetchTest} for that path, driven by a fake executor.
 */
class DevSceneTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path WEBMINER_MOD_DIR = Path.of("resources", "mods", "webminer");
    private static final ContentId WEB_MINER_ID = ContentId.of("webminer:web_miner");

    // Generous — even the slowest single chain (CHASSIS, 15 ticks/batch, fed from its own
    // pre-stocked supply chests, not from any other chain here) finishes several batches well
    // within this many ticks.
    private static final int TICKS = 2000;

    private static World devSceneWorld() {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WEBMINER_MOD_DIR));
        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(),
                GfxConfig.GRID_W, GfxConfig.GRID_H);
        DevScene.build(world);
        return world;
    }

    /**
     * The dev scene skips the {@code webminer} chain when that mod is absent, which is right for a
     * dev scene and wrong for this test: without this assertion, a webminer that fails to load
     * leaves every other test here green while covering nothing. That is not hypothetical — the
     * mod's first run as a real jar was silently skipped ("cannot get() before freeze()"), and the
     * only visible sign was a WARNING nobody reads in a passing build.
     */
    @Test
    void theWebMinerModActuallyLoadedRatherThanBeingSilentlySkipped() {
        World world = devSceneWorld();

        assertTrue(world.peek(1, 41).isPresent(),
                "the webminer mod's own chain is missing — its jar failed to load and the dev scene skipped it");
        assertEquals(WEB_MINER_ID, world.peek(1, 41).orElseThrow().prototypeId());
    }

    @Test
    void everyRecipeChainActuallyProducesItsOutput() {
        World world = devSceneWorld();

        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        assertOutput(world, 10, 5, VanillaItems.IRON_PLATE);
        assertOutput(world, 10, 8, VanillaItems.BRONZE_PLATE);
        assertOutput(world, 10, 11, VanillaItems.GEAR);
        assertOutput(world, 10, 14, VanillaItems.MECHANISM);
        assertOutput(world, 10, 17, VanillaItems.ENGINE);
        assertOutput(world, 10, 20, VanillaItems.CHASSIS);
        assertOutput(world, 10, 23, VanillaItems.ALLOY_PLATE);
        assertOutput(world, 10, 26, VanillaItems.ALLOY_GEAR);
    }

    /** The deliberately broken miner must actually stay broken — a live demonstration of NO_ORE, not an accident. */
    @Test
    void thePlantedBrokenMinerNeverMinesAnything() {
        World world = devSceneWorld();

        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        assertTrue(world.peek(1, 1).orElseThrow().heldItem().isEmpty(),
                "the stranded miner sits off every ore patch on purpose — it must never actually mine");
    }

    private static void assertOutput(World world, int x, int y, ItemType expected) {
        Chest output = (Chest) world.peek(x, y).orElseThrow();
        assertTrue(output.amount(expected) > 0,
                "(" + x + "," + y + ")'s output chest must have accumulated at least one " + expected
                        + " after " + TICKS + " ticks");
    }
}
