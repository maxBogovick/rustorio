package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Recipe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A balance mod — the most ordinary kind of mod there is in this genre — used to be impossible.
 * Recipes were appended to a plain list with no identity of their own, so "make someone else's
 * smelting twice as slow" had no handle to grab: a mod could only add a second, competing recipe
 * next to the one it meant to change, and could never take one out at all.
 *
 * <p>Now a recipe is registered content like an item or a building, addressed by its own id, so the
 * same {@code update}/{@code remove} an item already had applies to it.
 */
class RecipePatchingTest {

    private static final ContentId SMELT = ContentId.of("base_mod:smelt");

    @TempDir
    Path tempDir;

    @Test
    void aLaterModDoublesTheTimeOfAnEarlierModsRecipeWithoutRedefiningIt() throws IOException {
        Path base = writeBaseMod();
        Path balance = writeCodeMod("balance_mod", "com.testmod.BalanceMod", """
                package com.testmod;
                public final class BalanceMod implements com.rustorio.api.mod.RustorioMod {
                    @Override
                    public void modifyContent(com.rustorio.api.mod.RegistrationContext ctx) {
                        com.rustorio.api.content.ContentId id =
                                com.rustorio.api.content.ContentId.of("base_mod:smelt");
                        ctx.recipes().update(id, old -> new com.rustorio.domain.Recipe(
                                old.id(), old.ingredients(), old.output(), old.time() * 2, old.type()));
                    }
                }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(base, balance));

        Recipe smelt = findById(game, SMELT).orElseThrow(
                () -> new AssertionError("правленый рецепт должен остаться в книге, а не исчезнуть"));
        assertEquals(10, smelt.time(), "мод баланса должен был удвоить время исходного рецепта (было 5)");
        assertEquals(1, game.recipes().all().size(),
                "правка — не добавление: второго конкурирующего рецепта появиться не должно");
    }

    @Test
    void aLaterModRemovesAnEarlierModsRecipeOutright() throws IOException {
        Path base = writeBaseMod();
        Path remover = writeCodeMod("remover_mod", "com.testmod.RemoverMod", """
                package com.testmod;
                public final class RemoverMod implements com.rustorio.api.mod.RustorioMod {
                    @Override
                    public void modifyContent(com.rustorio.api.mod.RegistrationContext ctx) {
                        ctx.recipes().remove(com.rustorio.api.content.ContentId.of("base_mod:smelt"));
                    }
                }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(base, remover));

        assertTrue(findById(game, SMELT).isEmpty(), "удалённый рецепт не должен остаться в книге");
        assertTrue(game.recipes().all().isEmpty(), "других рецептов в этой сборке нет");
    }

    private static Optional<Recipe> findById(LoadedGame game, ContentId id) {
        return game.recipes().all().stream().filter(recipe -> recipe.id().equals(id)).findFirst();
    }

    /** One item and one recipe with a known id and a known time, so a later mod has something concrete to change. */
    private Path writeBaseMod() throws IOException {
        Path dir = tempDir.resolve("base_mod");
        Files.createDirectories(dir.resolve("content").resolve("items"));
        Files.createDirectories(dir.resolve("content").resolve("recipes"));
        Files.writeString(dir.resolve("mod.json"), """
                { "id": "base_mod", "version": "1.0.0", "dependencies": [] }
                """);
        Files.writeString(dir.resolve("content").resolve("items").resolve("ore.json"), """
                { "path": "ore", "label": "Ore", "colorRgb": "#112233", "shape": "CIRCLE" }
                """);
        // The file name is the recipe's id — "base_mod:smelt" — which is what the mods below name.
        Files.writeString(dir.resolve("content").resolve("recipes").resolve("smelt.json"), """
                { "ingredients": ["ore"], "output": "ore", "time": 5, "kind": "FURNACE" }
                """);
        return dir;
    }

    private Path writeCodeMod(String modId, String entryPointClassName, String source) throws IOException {
        Path dir = tempDir.resolve(modId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("mod.json"), """
                { "id": "%s", "version": "1.0.0", "entryPoint": "%s",
                  "dependencies": [{ "modId": "base_mod", "range": ">=1.0.0" }] }
                """.formatted(modId, entryPointClassName));
        TestModJarBuilder.build(dir.resolve(modId + ".jar"),
                Map.of(entryPointClassName, source),
                Map.of("com.rustorio.api.mod.RustorioMod", entryPointClassName));
        return dir;
    }
}
