package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
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
 * <p>The full history of what broke, in order: (1) {@code banana}'s ingredient being {@code coal}
 * itself is impossible — {@link Furnace#accept} always treats {@code COAL} as fuel for a {@code
 * FURNACE}-kind prototype with a {@code fuelItem}, never as a recipe ingredient. (2) {@code
 * iron_plate} looked collision-free ({@code RecipeBook}'s constructor only rejects an EXACT
 * duplicate ingredient set) but was still AMBIGUOUS with the vanilla {@code alloy_plate} recipe's
 * own {@code [iron_plate, bronze_plate]} — both lived in the SAME shared FURNACE pool, so {@link
 * Furnace#accept} refused to guess between them. (3) The actual fix ("создание своего архетипа" —
 * a private recipe pool a custom building can name via JSON, no Java): {@code voron.json} has no
 * {@code "kind"} of its own, defaulting to its own private pool ({@code rustorio:voron}, its own
 * id — see {@code BuildingJsonLoader}), and {@code banana.json} explicitly joins THAT pool ({@code
 * "kind": "voron"}) instead of the shared vanilla one. {@code iron_plate} is now unambiguous for
 * voron — its private pool has exactly one recipe — even though the SAME item is still ambiguous
 * in the vanilla FURNACE pool (this test proves BOTH halves, so private-pool isolation is the
 * thing actually verified, not just "banana happens to use a different item now").
 */
class VoronBananaAcceptanceTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");

    @Test
    void voronCooksBananaOreFromCoalAndIronPlateInItsOwnPrivatePoolWithNoAmbiguity() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));
        BuildingFactory factory = new BuildingFactory(
                com.rustorio.domain.PatchOreLayout.standard(), game.recipes(), game.items(), game.buildings());

        World world = new World(4, 4, factory);
        world.restoreBuilding(1, 0, new Chest());

        Furnace voron = (Furnace) factory.create(ContentId.of("rustorio:voron"), Direction.RIGHT);
        world.restoreBuilding(0, 0, voron);

        ItemType coal = game.items().get(ContentId.of("rustorio:coal"));
        ItemType ironPlate = game.items().get(ContentId.of("rustorio:iron_plate"));
        ItemType bananaOre = game.items().get(ContentId.of("rustorio:banana_ore"));

        assertTrue(voron.accept(world, coal), "voron must accept coal as fuel");
        // The actual bug this whole feature exists to fix: iron_plate is ALSO one of alloy_plate's
        // two ingredients in the shared vanilla FURNACE pool — without its own private pool, voron
        // would see two candidate recipes for iron_plate and refuse to guess, exactly as it used to.
        assertTrue(voron.accept(world, ironPlate),
                "voron must accept iron_plate unambiguously — its own private 'voron' pool has only one recipe for it");

        for (int i = 0; i < 10; i++) {
            world.tick();
        }

        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, chest.amount(bananaOre), "voron must have actually cooked and delivered banana_ore");
    }

    /**
     * The other half of the isolation proof: a VANILLA furnace, in the SAME loaded game, can still
     * only ever reach the vanilla FURNACE recipes (including {@code alloy_plate}, the one
     * legitimately ambiguous with {@code iron_plate} as an ingredient before banana moved out of
     * this pool) — {@code banana_ore} is reachable from voron's own private pool alone.
     * Symmetrically, voron can never reach {@code alloy_plate}. Neither pool leaked into the
     * other; they coexist, not merge.
     */
    @Test
    void aVanillaFurnaceInTheSameGameNeverReachesBananaOreAndVoronNeverReachesAlloyPlate() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));
        BuildingFactory factory = new BuildingFactory(
                com.rustorio.domain.PatchOreLayout.standard(), game.recipes(), game.items(), game.buildings());

        Furnace vanillaFurnace = (Furnace) factory.create(BuildingType.FURNACE, Direction.RIGHT);
        Furnace voron = (Furnace) factory.create(ContentId.of("rustorio:voron"), Direction.RIGHT);

        ItemType bananaOre = game.items().get(ContentId.of("rustorio:banana_ore"));
        ItemType alloyPlate = game.items().get(ContentId.of("rustorio:alloy_plate"));

        assertTrue(vanillaFurnace.possibleRecipes().stream().anyMatch(r -> r.output().equals(alloyPlate)),
                "the shared vanilla FURNACE pool still has alloy_plate, untouched");
        assertTrue(vanillaFurnace.possibleRecipes().stream().noneMatch(r -> r.output().equals(bananaOre)),
                "banana_ore must NOT be reachable from the shared vanilla pool — it lives in voron's own private one");
        assertTrue(voron.possibleRecipes().stream().anyMatch(r -> r.output().equals(bananaOre)),
                "voron's own private pool has banana_ore");
        assertTrue(voron.possibleRecipes().stream().noneMatch(r -> r.output().equals(alloyPlate)),
                "voron must NOT reach alloy_plate — that recipe lives in the separate shared vanilla pool");
    }
}
