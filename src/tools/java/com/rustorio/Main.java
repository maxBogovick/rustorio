package com.rustorio;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModDirectories;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;

/**
 * Headless entry point: loads every mod, builds a small iron chain on the loaded content, ticks it,
 * and prints what happens — no window, no libGDX. Enough to see the simulation work on its own;
 * {@code com.graphics.Main} is the windowed, playable game built on the same {@link World}.
 *
 * <p>Goes through {@link ModLoader} rather than {@code new World(12, 8)}'s vanilla shortcut, so this
 * is a real headless exercise of the mod-loading path — the one the windowed game also takes, and
 * the only one available at all in an environment with no display. It also means the demo below
 * reflects installed mods: a mod that changes the iron smelting recipe changes what this prints.
 *
 * <p>Items are resolved out of the loaded registry, never from {@code VanillaItems}: {@code
 * RecipeBook} matches ingredients by identity ({@code ==}), and the registry a world was built on
 * hands out different {@link ItemType} instances than the vanilla-only constant pool does.
 */
public final class Main {

    private static final int TICKS = 120;
    private static final int REPORT_EVERY = 20;
    private static final Path MODS_ROOT = Path.of("resources", "mods");
    /** Named by id, not by importing the mod: this demo must not depend on a mod's Java at compile time. */
    private static final ContentId WEB_MINER_ID = ContentId.of("webminer:web_miner");

    /** How long to keep waiting, after the fixed {@link #TICKS} loop, for the web miner's fetch to actually resolve — real network latency, not simulation time. */
    private static final long WEB_FETCH_TIMEOUT_MS = 8_000;

    public static void main(String[] args) throws InterruptedException {
        LoadedGame content = ModLoader.loadAll(ModDirectories.discover(MODS_ROOT));
        // The web miner arrives from resources/mods/webminer like any other mod — its buildings and
        // its fetch service both, registered from inside its own jar. This demo used to merge that
        // one mod in by hand right here.
        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(), 12, 8);
        try {
            ItemType coal = item(content, "coal");
            ItemType ironOre = item(content, "iron_ore");
            ItemType ironPlate = item(content, "iron_plate");

            // A miner needs ore under it — (6, 5) sits at the center of the standard map's first iron
            // patch, so it's guaranteed to have ore regardless of how the patch layout evolves.
            world.placeMiner(6, 5);
            world.placeBelt(7, 5, Direction.RIGHT);
            world.placeFurnace(8, 5, Direction.RIGHT);
            world.placeBelt(9, 5, Direction.RIGHT);
            world.placeChest(10, 5);
            Building furnace = world.peek(8, 5).orElseThrow();

            // A real web miner, on a row of its own — NEEDS_PASSABLE_TERRAIN only, no ore required.
            world.place(WEB_MINER_ID, 0, 0, Direction.RIGHT);
            world.placeBelt(1, 0, Direction.RIGHT);
            world.placeChest(2, 0);
            Chest webChest = (Chest) world.peek(2, 0).orElseThrow();

            System.out.println("""
                    Rustorio — headless run
                    Miner(6,5) -> Belt(7,5) -> Furnace(8,5) -> Belt(9,5) -> Chest(10,5)
                    WebMiner(0,0) -> Belt(1,0) -> Chest(2,0)  [real HTTP GET https://api.github.com]
                    """);

            for (int tick = 1; tick <= TICKS; tick++) {
                // FURNACE needs coal to smelt at all — a real coal line would need this tiny 12x8 demo
                // world enlarged to actually reach one of the standard map's coal patches (none fall
                // within these bounds) plus a second belt run converging on the furnace from another
                // side, which is exactly the "ore one side, coal the other" planning puzzle — real
                // gameplay, not a 20-line headless demo's job to model. accept() caps at its own fuel
                // limit and returns false once full, so this is a harmless no-op most ticks.
                furnace.accept(world, coal);
                world.tick();
                if (tick % REPORT_EVERY == 0) {
                    System.out.printf("tick %3d: ore mined %2d, plates smelted %2d%n",
                            tick, world.stats().total(ironOre), world.stats().total(ironPlate));
                }
            }

            // The fixed TICKS loop above runs faster than any real network round-trip (no real-time
            // pacing headless), so the web fetch is almost certainly still in flight when it ends —
            // this waits, with real sleeps, specifically for IT rather than padding TICKS with a
            // guess. Bounded so a genuinely offline environment still exits instead of hanging.
            long deadline = System.currentTimeMillis() + WEB_FETCH_TIMEOUT_MS;
            while (webChest.count() == 0 && System.currentTimeMillis() < deadline) {
                world.tick();
                Thread.sleep(20);
            }
            if (webChest.count() > 0) {
                ItemType delivered = webChest.contents().keySet().iterator().next();
                System.out.println("Web miner delivered: " + delivered.id()
                        + " — a real outbound HTTP GET completed and reached the belt.");
            } else {
                System.out.println("Web miner produced nothing within " + WEB_FETCH_TIMEOUT_MS
                        + "ms — either this environment has no outbound network access, or the request failed/timed out.");
            }
        } finally {
            // One call regardless of how many mods hold a background resource — the world owns
            // whatever they registered.
            world.closeServices();
        }
    }

    private static ItemType item(LoadedGame content, String path) {
        return content.items().get(new ContentId("rustorio", path));
    }
}
