package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.TechType;
import com.rustorio.domain.VanillaFluids;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaTechs;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaBuildings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The main test of the whole Phase 7 architecture: {@code resources/mods/rustorio} — the SAME
 * built-in content {@link VanillaItems}/{@link VanillaBuildings}/{@link RecipeBook#standard()}
 * already register in Java — loaded through the identical {@link ModLoader} path a third-party mod
 * goes through, must produce number-for-number the same game content. If it doesn't, the mod API is
 * not actually sufficient to express the game's own vanilla content, no matter how many other tests
 * pass.
 *
 * <p>Deliberately does NOT replace {@code VanillaItems}/{@code VanillaBuildings}/{@code
 * RecipeBook.standard()} themselves, or {@code BuildingFactory.standard()}/{@code GameScreen}'s own
 * bootstrap — those stay exactly as they are, relied on by dozens of existing tests. This is a
 * second, independent path proving the same result is reachable, not a replacement of the first.
 */
class VanillaAsModParityTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");

    @TempDir
    Path tempDir;

    @Test
    void rustorioModDirLoadsAlongsideAnUnrelatedThirdPartyModWithNoSpecialCasing() throws IOException {
        Path thirdParty = tempDir.resolve("third_party");
        Files.createDirectories(thirdParty.resolve("content").resolve("items"));
        Files.writeString(thirdParty.resolve("mod.json"), """
                { "id": "third_party", "version": "1.0.0", "dependencies": [] }
                """);
        Files.writeString(thirdParty.resolve("content").resolve("items").resolve("widget.json"), """
                { "path": "widget", "label": "Widget", "colorRgb": "#123456", "shape": "SQUARE" }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, thirdParty));

        assertTrue(game.items().peek(ContentId.of("rustorio:iron_ore")).isPresent(), "vanilla content must still be there");
        assertTrue(game.items().peek(ContentId.of("third_party:widget")).isPresent(), "third-party content must load alongside it");
    }

    @Test
    void itemsMatchVanillaItemsNumberForNumber() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        List<ItemType> fromJson = game.items().iterate();
        List<ItemType> fromJava = VanillaItems.frozen().iterate();

        assertEquals(fromJava.size(), fromJson.size(), "same number of items");
        for (int i = 0; i < fromJava.size(); i++) {
            ItemType java = fromJava.get(i);
            ItemType json = fromJson.get(i);
            assertEquals(java.id(), json.id());
            assertEquals(java.label(), json.label());
            assertEquals(java.researchGrade(), json.researchGrade(), java.id() + ": researchGrade");
            assertEquals(java.colorRgb(), json.colorRgb(), java.id() + ": colorRgb");
            assertEquals(java.shape(), json.shape(), java.id() + ": shape");
        }
    }

    @Test
    void fluidsMatchVanillaFluidsNumberForNumber() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        List<FluidType> fromJson = game.fluids().iterate();
        List<FluidType> fromJava = VanillaFluids.frozen().iterate();

        assertEquals(fromJava.size(), fromJson.size(), "same number of fluids");
        for (int i = 0; i < fromJava.size(); i++) {
            FluidType java = fromJava.get(i);
            FluidType json = fromJson.get(i);
            assertEquals(java.id(), json.id());
            assertEquals(java.label(), json.label());
            assertEquals(java.colorRgb(), json.colorRgb(), java.id() + ": colorRgb");
        }
    }

    @Test
    void techsMatchVanillaTechsNumberForNumber() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        List<TechType> fromJson = game.techs().iterate();
        List<TechType> fromJava = VanillaTechs.frozen().iterate();

        assertEquals(fromJava.size(), fromJson.size(), "same number of technologies");
        for (int i = 0; i < fromJava.size(); i++) {
            TechType java = fromJava.get(i);
            TechType json = fromJson.get(i);
            assertEquals(java.id(), json.id());
            assertEquals(java.label(), json.label(), java.id() + ": label");
            assertEquals(java.cost(), json.cost(), java.id() + ": cost");
            assertEquals(java.prerequisites(), json.prerequisites(), java.id() + ": prerequisites");
            assertEquals(java.effects(), json.effects(), java.id() + ": effects");
        }
    }

    @Test
    void buildingsMatchVanillaBuildingsNumberForNumber() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        List<BuildingPrototype> fromJson = game.buildings().iterate();
        List<BuildingPrototype> fromJava = VanillaBuildings.frozen().iterate();

        assertEquals(fromJava.size(), fromJson.size(), "same number of building prototypes");
        for (int i = 0; i < fromJava.size(); i++) {
            BuildingPrototype java = fromJava.get(i);
            BuildingPrototype json = fromJson.get(i);
            assertEquals(java.id(), json.id());
            assertEquals(java.label(), json.label(), java.id() + ": label");
            assertEquals(java.footprintWidth(), json.footprintWidth(), java.id() + ": footprintWidth");
            assertEquals(java.footprintHeight(), json.footprintHeight(), java.id() + ": footprintHeight");
            assertEquals(java.cost().item().id(), json.cost().item().id(), java.id() + ": cost item");
            assertEquals(java.cost().amount(), json.cost().amount(), java.id() + ": cost amount");
            assertEquals(java.texture(), json.texture(), java.id() + ": texture");
            assertEquals(java.bufferMax(), json.bufferMax(), java.id() + ": bufferMax");
            assertEquals(java.speedMultiplier(), json.speedMultiplier(), java.id() + ": speedMultiplier");
            assertEquals(java.acceptsSpeedEffects(), json.acceptsSpeedEffects(), java.id() + ": acceptsSpeedEffects");
            // The traits are what make a pump a pump rather than a differently-priced miner: if the
            // JSON path lost them, the vanilla-as-data claim would be false in exactly the place a
            // fluid or power mod would try to copy from.
            //
            // The whole bag in ONE assertion, not a line per property: a trait added later is
            // compared here the day it exists, which is the point of the bag. Traits compares by
            // value and prints itself, so a mismatch names the trait and both sides.
            assertEquals(java.traits(), json.traits(), java.id() + ": traits");
            assertEquals(java.fuelItem(), json.fuelItem(), java.id() + ": fuelItem");
        }
    }

    @Test
    void recipesMatchRecipeBookStandardAsASet() {
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));

        // Compared as a SET, not a list: content/recipes/*.json is read in FILENAME order, which
        // has no reason to match RecipeBook.standard()'s Java declaration order — only the game
        // content itself (which recipes exist) is what "loads the same way" is supposed to prove.
        Set<String> fromJson = canonicalForms(game.recipes());
        Set<String> fromJava = canonicalForms(RecipeBook.standard());

        assertEquals(fromJava, fromJson);
    }

    private static Set<String> canonicalForms(RecipeBook book) {
        return book.all().stream().map(VanillaAsModParityTest::canonicalForm).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * A recipe's content, independent of declaration order — ingredient order is sorted too, since
     * {@code Recipe}'s own javadoc says it's display-only.
     *
     * <p>Includes the recipe's own id. A mod that adjusts "the vanilla iron recipe" names it by id,
     * so the two loading paths agreeing on ingredients while disagreeing on the NAME would leave
     * that mod working through one path and silently doing nothing through the other.
     */
    private static String canonicalForm(Recipe recipe) {
        String ingredients = recipe.ingredients().stream()
                .map(item -> item.id().toString())
                .sorted(Comparator.naturalOrder())
                .reduce((a, b) -> a + "+" + b)
                .orElse("");
        return recipe.id() + ": " + ingredients + "->" + recipe.output().id() + "@" + recipe.time()
                + "#" + recipe.type();
    }
}
