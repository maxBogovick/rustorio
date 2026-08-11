package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code ModLoader#validateContent}'s new kind checks (the actual guarantee behind the content
 * editor's kind-picker dropdown, see {@code app.js}'s {@code renderKindSelectOptions}): a recipe's
 * own pool and a building's own explicit {@code "kind"} must resolve to something real — a
 * registered {@link com.rustorio.api.content.model.RecipeKind}, a vanilla {@code BuildingType}, or another
 * building's own id — never a silent no-op typo. A building's DEFAULT private pool (no {@code
 * "kind"} field at all) needs no separate registration at all — see the "zero setup" tests below.
 */
class ModLoaderKindValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void recipeWithAnUnknownKindSkipsThatModAndSaysWhy() throws IOException {
        Path mod = modDir("test_mod");
        writeModJson(mod, "test_mod");
        writeItem(mod, "gadget", "Gadget", "#112233", "SQUARE");
        writeRecipe(mod, "gadget", "gadget", 5, "totally_bogus_kind");

        LoadedGame game = ModLoader.loadAll(List.of(mod));

        assertEquals(1, game.skippedMods().size(), "мод с несуществующим пулом рецептов не должен попасть в игру");
        SkippedMod skipped = game.skippedMods().get(0);
        assertEquals(new ModId("test_mod"), skipped.id(), "пропущен должен быть именно виновный мод");
        assertTrue(skipped.reason().contains("totally_bogus_kind"),
                "причина должна называть опечатку, а не общие слова: " + skipped.reason());
    }

    @Test
    void buildingWithAnUnknownExplicitKindSkipsThatModAndSaysWhy() throws IOException {
        Path mod = modDir("test_mod");
        writeModJson(mod, "test_mod");
        writeItem(mod, "gadget", "Gadget", "#112233", "SQUARE");
        writeBuilding(mod, "my_furnace", "gadget", "totally_bogus_kind", null);

        LoadedGame game = ModLoader.loadAll(List.of(mod));

        assertEquals(1, game.skippedMods().size(), "мод со зданием, указавшим несуществующий пул, не должен попасть в игру");
        assertTrue(game.skippedMods().get(0).reason().contains("totally_bogus_kind"),
                "причина должна называть опечатку: " + game.skippedMods().get(0).reason());
    }

    @Test
    void buildingsDefaultPrivatePoolNeedsNoRegistrationAtAll() throws IOException {
        Path mod = modDir("test_mod");
        writeModJson(mod, "test_mod");
        writeItem(mod, "gadget", "Gadget", "#112233", "SQUARE");
        // No "kind" field at all — must still load: the default (own id) is valid by construction.
        writeBuilding(mod, "my_furnace", "gadget", null, null);

        assertDoesNotThrow(() -> ModLoader.loadAll(List.of(mod)));
    }

    @Test
    void recipeJoiningAnotherBuildingsOwnDefaultPrivateKindSucceeds() throws IOException {
        Path mod = modDir("test_mod");
        writeModJson(mod, "test_mod");
        writeItem(mod, "gadget", "Gadget", "#112233", "SQUARE");
        writeBuilding(mod, "my_furnace", "gadget", null, null); // private pool = "test_mod:my_furnace"
        writeRecipe(mod, "gadget", "gadget", 5, "my_furnace");

        LoadedGame game = ModLoader.loadAll(List.of(mod));
        assertTrue(game.recipes().forKind(ContentId.of("test_mod:my_furnace")).stream()
                .anyMatch(r -> r.output().id().equals(ContentId.of("test_mod:gadget"))));
    }

    @Test
    void recipeJoiningARegisteredKindSucceedsAndTheKindShowsUpInLoadedGame() throws IOException {
        Path mod = modDir("test_mod");
        writeModJson(mod, "test_mod");
        writeItem(mod, "gadget", "Gadget", "#112233", "SQUARE");
        writeKind(mod, "shared_pool", "Shared Pool");
        writeBuilding(mod, "my_furnace", "gadget", "shared_pool", null);
        writeRecipe(mod, "gadget", "gadget", 5, "shared_pool");

        LoadedGame game = ModLoader.loadAll(List.of(mod));
        assertTrue(game.kinds().peek(ContentId.of("test_mod:shared_pool")).isPresent());
        assertTrue(game.recipes().forKind(ContentId.of("test_mod:shared_pool")).stream()
                .anyMatch(r -> r.output().id().equals(ContentId.of("test_mod:gadget"))));
    }

    private Path modDir(String name) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    private void writeModJson(Path modDir, String id) throws IOException {
        Files.writeString(modDir.resolve("mod.json"), """
                { "id": "%s", "version": "1.0.0", "dependencies": [] }
                """.formatted(id));
    }

    private void writeItem(Path modDir, String path, String label, String colorRgb, String shape) throws IOException {
        Path itemsDir = modDir.resolve("content").resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve(path + ".json"), """
                { "path": "%s", "label": "%s", "colorRgb": "%s", "shape": "%s" }
                """.formatted(path, label, colorRgb, shape));
    }

    private void writeRecipe(Path modDir, String ingredientPath, String outputPath, int time, String kind) throws IOException {
        Path recipesDir = modDir.resolve("content").resolve("recipes");
        Files.createDirectories(recipesDir);
        Files.writeString(recipesDir.resolve(outputPath + ".json"), """
                { "ingredients": ["%s"], "output": "%s", "time": %d, "kind": "%s" }
                """.formatted(ingredientPath, outputPath, time, kind));
    }

    private void writeKind(Path modDir, String path, String label) throws IOException {
        Path kindsDir = modDir.resolve("content").resolve("kinds");
        Files.createDirectories(kindsDir);
        Files.writeString(kindsDir.resolve(path + ".json"), """
                { "path": "%s", "label": "%s" }
                """.formatted(path, label));
    }

    /** {@code costItemPath} must already be registered by {@link #writeItem}; {@code kind}/{@code fuel} are omitted (JSON field left out) when {@code null}. */
    private void writeBuilding(Path modDir, String path, String costItemPath, String kind, String fuel) throws IOException {
        Path buildingsDir = modDir.resolve("content").resolve("buildings");
        Files.createDirectories(buildingsDir);
        String kindField = kind == null ? "" : ", \"kind\": \"" + kind + "\"";
        String fuelField = fuel == null ? "" : ", \"fuel\": \"" + fuel + "\"";
        Files.writeString(buildingsDir.resolve(path + ".json"), """
                { "path": "%s", "label": "%s", "archetype": "FURNACE"%s%s,
                  "cost": { "item": "%s", "amount": 1 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold" }
                """.formatted(path, path, kindField, fuelField, costItemPath));
    }
}
