package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaCategories;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A building's build-panel tab is DATA on its prototype, declared in the same JSON as its cost and
 * its sprite.
 *
 * <p>The panel used to group by {@code id().namespace()} — by which MOD wrote a building, not by
 * what it is for. With one mod that reads as harmless; with four it makes the panel unbrowsable,
 * which is how the hotbar ended up carrying all twenty-nine prototypes and running off both edges
 * of the screen.
 *
 * <p>{@code "category"} is deliberately optional. Every mod written before it existed must keep
 * loading, so an absent key means {@link VanillaCategories#OTHER} rather than a load failure —
 * {@code sandbox:voron} is a real, checked-in example of exactly that.
 */
class BuildingCategoryTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path SANDBOX_MOD_DIR = Path.of("resources", "mods", "sandbox");

    private static BuildingPrototype prototype(LoadedGame game, String id) {
        return game.buildings().get(ContentId.of(id));
    }

    /**
     * The whole path in one assertion: the key is written in {@code content/buildings/pipe.json},
     * read by the loader, stored as a trait, and read back by the one accessor the panel uses.
     */
    @Test
    void aCategoryDeclaredInJsonReachesThePrototype() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        assertEquals(VanillaCategories.FLUIDS, VanillaCategories.of(prototype(game, "rustorio:pipe")),
                "a pipe belongs under Fluids, and that fact lives in its own JSON");
        assertEquals(VanillaCategories.MINING, VanillaCategories.of(prototype(game, "rustorio:miner")));
        assertEquals(VanillaCategories.LOGISTICS, VanillaCategories.of(prototype(game, "rustorio:belt")));
        assertEquals(VanillaCategories.PRODUCTION, VanillaCategories.of(prototype(game, "rustorio:furnace")));
        assertEquals(VanillaCategories.POWER, VanillaCategories.of(prototype(game, "rustorio:generator")));
    }

    /**
     * A bare name resolves against {@code rustorio}, not against the mod that wrote it — the same
     * convention every other reference in a content file already follows. Written as {@code
     * "category": "fluids"} in vanilla's own files, which is what makes this assertion meaningful:
     * had the loader namespaced it to the declaring mod, this would be {@code rustorio:fluids}
     * only by luck.
     */
    @Test
    void aBareCategoryNameResolvesAgainstVanillaRatherThanTheDeclaringMod() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        ContentId category = VanillaCategories.of(prototype(game, "rustorio:tank"));

        assertEquals("rustorio", category.namespace());
        assertEquals(VanillaCategories.FLUIDS, category);
    }

    /**
     * The compatibility half, and the reason the key is optional: {@code sandbox:voron} declares no
     * category at all and still loads, landing in the catch-all tab. A mod written a year before
     * this feature must not need editing.
     */
    @Test
    void aBuildingThatDeclaresNoCategoryLandsInOtherRatherThanFailingToLoad() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, SANDBOX_MOD_DIR));

        BuildingPrototype voron = prototype(game, "sandbox:voron");

        assertEquals(VanillaCategories.OTHER, VanillaCategories.of(voron),
                "an undeclared category is a default, never an error");
        assertNotEquals(VanillaCategories.OTHER, VanillaCategories.of(prototype(game, "rustorio:pipe")),
                "and the default must not be swallowing everything — vanilla still categorises");
    }

    /** Tab order is a fixed list, never a set: an order that differs between JVM runs would move tabs under the player's cursor between launches. */
    @Test
    void tabOrderIsFixedAndEndsWithTheCatchAll() {
        List<ContentId> order = VanillaCategories.all();

        assertEquals(6, order.size());
        assertEquals(VanillaCategories.MINING, order.get(0));
        assertEquals(VanillaCategories.OTHER, order.get(order.size() - 1),
                "the catch-all belongs last — it is where unlabelled content falls, not a headline tab");
        assertEquals(order, VanillaCategories.all(), "the same order every call, and every run");
    }
}
