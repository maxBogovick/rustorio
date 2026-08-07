package com.rustorio.domain.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A modded building must be treated as ITSELF, not as the vanilla archetype whose Java class it
 * happens to reuse.
 *
 * <p>The distinction had no teeth while every building was vanilla, and {@code Building.type()} —
 * which answers "which of the twelve vanilla kinds am I categorised as" — was quietly used as if it
 * were the building's identity. It is not: a modded prototype borrows an archetype for its
 * behaviour and its hotbar slot, and keeps its own cost, its own rules and its own id.
 *
 * <p>{@code sandbox:voron} makes the gap concrete and costly. It reuses the {@code FURNACE}
 * archetype but costs ONE COAL, where a vanilla furnace costs five iron plates. Anything resolving
 * it by {@code type()} therefore reads a completely different building's data — and in the case of
 * demolition, hands the player five iron plates they never paid.
 */
class ModdedBuildingIdentityTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path SANDBOX_MOD_DIR = Path.of("resources", "mods", "sandbox");
    private static final ContentId VORON_ID = ContentId.of("sandbox:voron");

    private static World moddedWorld() {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, SANDBOX_MOD_DIR));
        return GameBootstrap.createWorld(content, PatchOreLayout.standard(), 8, 8);
    }

    private static ItemType item(World world, String id) {
        return world.buildingFactory().items().get(ContentId.of(id));
    }

    /**
     * Build it, demolish it, and end up exactly where you started. Before the fix this printed a
     * profit: the refund was looked up by {@code type()}, so a one-coal building paid back a
     * five-iron-plate furnace — a duplication glitch anyone could run in a loop.
     */
    @Test
    void demolishingAModdedBuildingRefundsItsOwnCostAndNotItsArchetypes() {
        World world = moddedWorld();
        ItemType coal = item(world, "rustorio:coal");
        ItemType ironPlate = item(world, "rustorio:iron_plate");
        world.creditItem(coal, 10); // the starting inventory ships none, and voron is priced in it
        int coalBefore = world.inventory().amount(coal);
        int ironBefore = world.inventory().amount(ironPlate);

        new ActionHistory().perform(world, new PlaceAction(VORON_ID, 3, 3));
        assertTrue(world.peek(3, 3).isPresent(), "the modded building must place at all for this to prove anything");
        assertEquals(coalBefore - 1, world.inventory().amount(coal),
                "voron costs one coal — if this is wrong the rest of the test means nothing");

        new ActionHistory().perform(world, new RemoveAction(3, 3));

        assertEquals(coalBefore, world.inventory().amount(coal),
                "demolition must give back what the building actually cost");
        assertEquals(ironBefore, world.inventory().amount(ironPlate),
                "demolition must not pay out the archetype's cost — that is free iron plates from nothing");
    }

    /**
     * Undoing that demolition must charge the same thing it refunded. Symmetry matters more than
     * the amount: a refund and a re-charge reading two different prototypes is a duplication glitch
     * whichever way round it is, and undo/redo is the cheapest loop to run it in.
     */
    @Test
    void undoingADemolitionChargesTheBuildingsOwnCostBack() {
        World world = moddedWorld();
        ItemType coal = item(world, "rustorio:coal");
        ItemType ironPlate = item(world, "rustorio:iron_plate");
        world.creditItem(coal, 10);
        new ActionHistory().perform(world, new PlaceAction(VORON_ID, 3, 3));
        int coalAfterPlacing = world.inventory().amount(coal);
        int ironAfterPlacing = world.inventory().amount(ironPlate);

        ActionHistory history = new ActionHistory();
        history.perform(world, new RemoveAction(3, 3));
        history.undo(world);

        assertEquals(coalAfterPlacing, world.inventory().amount(coal),
                "undoing a demolition must cost exactly what the demolition refunded");
        assertEquals(ironAfterPlacing, world.inventory().amount(ironPlate),
                "and must not touch an unrelated item the archetype happens to be priced in");
        assertEquals(VORON_ID, world.peek(3, 3).orElseThrow().prototypeId(),
                "the restored building must still be the modded one");
    }
}
