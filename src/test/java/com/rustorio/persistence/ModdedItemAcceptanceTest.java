package com.rustorio.persistence;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The phase's own acceptance test: a brand-new item, never seen by anything in {@code src/main},
 * is registered, mined, stored, and survives a save/load round trip — zero core edits. If this
 * ever needs one, the point of the whole {@code Item} enum -> {@code ItemType} registry migration
 * failed somewhere.
 */
class ModdedItemAcceptanceTest {

    private static final ItemType COPPER_ORE =
            new ItemType(ContentId.of("test:copper_ore"), "Copper Ore", false, 0xB8_73_33, ItemShape.CIRCLE);

    /** Vanilla items plus {@link #COPPER_ORE} — what a game running this one extra mod would freeze at startup. */
    private static Registry<ItemType> gameItems() {
        Registry<ItemType> items = new Registry<>();
        VanillaItems.registerAll(items);
        items.register(COPPER_ORE.id(), COPPER_ORE);
        items.freeze();
        return items;
    }

    /** A single passable cell at (0, 0) with {@link #COPPER_ORE} under it — the smallest map this scenario needs. */
    private static OreLayout singleCopperCellLayout() {
        return new OreLayout() {
            @Override
            public Optional<ItemType> oreAt(int x, int y) {
                return x == 0 && y == 0 ? Optional.of(COPPER_ORE) : Optional.empty();
            }

            @Override
            public Optional<ItemType> extract(int x, int y) {
                return oreAt(x, y);
            }

            @Override
            public OreLayoutId id() {
                return new OreLayoutId("test-copper", 0, 4, 4);
            }

            @Override
            public Optional<ItemType> terrainAt(int x, int y) {
                return Optional.empty();
            }

            @Override
            public Map<Integer, Integer> depletionSnapshot() {
                return Map.of();
            }

            @Override
            public void restoreDepletion(Map<Integer, Integer> snapshot) {
                // nothing to restore — this layout's ore never depletes
            }
        };
    }

    @Test
    void aBrandNewItemCanBeMinedStoredAndSurviveASaveLoadRoundTrip(@TempDir Path dir) {
        Registry<ItemType> items = gameItems();
        // Same registry the JsonSaveRepository below is configured with: a building's own Codec
        // resolves ItemType values (e.g. a Chest's contents map keys) through the BuildingFactory's
        // own registry, not JsonSaveRepository's — a real game/mod runtime only ever has ONE
        // registry, so the two must agree here too.
        BuildingFactory factory = new BuildingFactory(singleCopperCellLayout(), RecipeBook.standard(), items);
        World world = new World(4, 4, factory);

        assertTrue(world.placeMiner(0, 0), "miner needs passable ground and ore under it");
        assertTrue(world.placeChest(1, 0));

        for (int i = 0; i < 4; i++) {
            world.tick();
        }

        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, chest.amount(COPPER_ORE), "one copper ore should have been mined and delivered by now");

        JsonSaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"), items);
        assertTrue(repository.save(world).succeeded());

        World reloaded = new World(4, 4, factory);
        assertTrue(repository.load(reloaded).succeeded());

        Building reloadedChestBuilding = reloaded.peek(1, 0).orElseThrow();
        Chest reloadedChest = (Chest) reloadedChestBuilding;
        assertEquals(1, reloadedChest.amount(COPPER_ORE),
                "the modded item must still be there, correctly identified, after a save/load round trip");
    }

    /** Sanity check that the registry actually holds both the vanilla set and the new item, so a failure above points at the game logic, not a broken fixture. */
    @Test
    void fixtureRegistryHasVanillaItemsPlusTheNewOre() {
        Registry<ItemType> items = gameItems();

        assertEquals(14, items.size(), "13 vanilla items + copper_ore");
        assertEquals(COPPER_ORE, items.get(ContentId.of("test:copper_ore")));
        assertEquals(VanillaItems.IRON_ORE, items.get(ContentId.of("rustorio:iron_ore")));
    }
}
