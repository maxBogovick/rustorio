package com.rustorio;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.building.Building;
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

    public static void main(String[] args) {
        LoadedGame content = ModLoader.loadAll(ModDirectories.discover(MODS_ROOT));
        // PatchOreLayout.standard(): the same fixed map BuildingFactory.standard() used before this
        // demo went through the loader, so the coordinates below still sit where they used to.
        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(), 12, 8);
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

        System.out.println("""
                Rustorio — headless run
                Miner(6,5) -> Belt(7,5) -> Furnace(8,5) -> Belt(9,5) -> Chest(10,5)
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
    }

    private static ItemType item(LoadedGame content, String path) {
        return content.items().get(new ContentId("rustorio", path));
    }
}
