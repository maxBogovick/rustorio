package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.world.World;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end reproduction of the live bug report: placing {@code voron} (a modded {@code FURNACE}
 * archetype building, {@code resources/mods/rustorio/content/buildings/voron.json}), feeding it
 * coal (fuel) plus its recipe's own ingredient, must actually cook and push out {@code banana_ore}
 * — not just fail to crash on load ({@code VanillaAsModParityTest}) or resolve the right sprite
 * ({@code com.rustorio.domain.building.ModdedBuildingTextureTest}).
 *
 * <p>Caught two real defects neither of those other tests could: (1) {@code banana}'s ingredient
 * being {@code coal} itself is impossible — {@link Furnace#accept} always treats {@code COAL} as
 * fuel for a {@code FURNACE} kind, never as a recipe ingredient, no matter what any recipe says; (2)
 * {@code iron_plate} looked collision-free ({@code RecipeBook}'s constructor only rejects an EXACT
 * duplicate ingredient set) but was still AMBIGUOUS with {@code alloy_plate}'s own {@code
 * [iron_plate, bronze_plate]} — {@link Furnace#accept} refuses an item matching more than one
 * candidate recipe until the player manually disambiguates via {@code cycleRecipe}, so voron never
 * actually accepted the iron_plate a belt was pushing into it. {@code gear} collides and is
 * ambiguous with neither.
 */
class VoronBananaAcceptanceTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");

    @Test
    void voronCooksBananaOreFromCoalAndGearWithNoRecipeAmbiguity() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));
        BuildingFactory factory = new BuildingFactory(
                com.rustorio.domain.PatchOreLayout.standard(), game.recipes(), game.items(), game.buildings());

        World world = new World(4, 4, factory);
        world.restoreBuilding(1, 0, new Chest());

        Furnace voron = (Furnace) factory.create(ContentId.of("rustorio:voron"), Direction.RIGHT);
        world.restoreBuilding(0, 0, voron);

        ItemType coal = game.items().get(ContentId.of("rustorio:coal"));
        ItemType gear = game.items().get(ContentId.of("rustorio:gear"));
        ItemType bananaOre = game.items().get(ContentId.of("rustorio:banana_ore"));

        assertTrue(voron.accept(world, coal), "voron must accept coal as fuel");
        // The actual bug: this used to return false because gear/iron_plate matched more than one
        // FURNACE recipe (or, before that, because coal can never be a real ingredient at all).
        assertTrue(voron.accept(world, gear), "voron must accept gear unambiguously as banana's own ingredient");

        for (int i = 0; i < 10; i++) {
            world.tick();
        }

        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, chest.amount(bananaOre), "voron must have actually cooked and delivered banana_ore");
    }
}
