package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The full lifecycle end to end: dependency-ordered rounds, peer content visibility across real
 * jar mods, content validation, and load-order determinism. {@code ModJarLoaderTest}/{@code
 * ModClassLoaderTest} already cover classloader isolation in depth; this class is about the
 * ORCHESTRATION on top of it.
 */
class ModLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void twoDataOnlyModsLoadWithoutAnyJavaCode() throws IOException {
        Path modA = modDir("data_a");
        writeModJson(modA, "data_a", "1.0.0", null, List.of());
        writeItem(modA, "gadget", "Gadget", "#112233", "SQUARE");

        Path modB = modDir("data_b");
        writeModJson(modB, "data_b", "1.0.0", null, List.of());
        writeItem(modB, "gizmo", "Gizmo", "#445566", "TRIANGLE");

        LoadedGame game = ModLoader.loadAll(List.of(modA, modB));

        assertTrue(game.items().peek(com.rustorio.api.content.ContentId.of("data_a:gadget")).isPresent());
        assertTrue(game.items().peek(com.rustorio.api.content.ContentId.of("data_b:gizmo")).isPresent());
    }

    @Test
    void aDependentModsRegisterContentSeesItsDependencysContentBecauseOfResolvedLoadOrderNotAlphabeticalOrder() throws IOException {
        // "alpha_uses" sorts BEFORE "zeta_provides" alphabetically — if the loader ignored the
        // declared dependency and just used directory-name order, alpha's own registerContent
        // would run first and its lookup below would fail. Depending on zeta forces the resolver
        // to load zeta first regardless of the names.
        Path zeta = modDir("zeta_provides");
        writeModJson(zeta, "zeta_provides", "1.0.0", "com.testmod.ZetaProvides", List.of());
        buildJarWithEntryPoint(zeta, "zeta_provides", "com.testmod.ZetaProvides", """
                package com.testmod;
                public final class ZetaProvides implements com.rustorio.api.mod.RustorioMod {
                    @Override
                    public void registerContent(com.rustorio.api.mod.RegistrationContext ctx) {
                        com.rustorio.api.content.ContentId id = com.rustorio.api.content.ContentId.of("zeta_provides:alloy");
                        ctx.items().register(id, new com.rustorio.domain.ItemType(
                                id, "Alloy", false, 0x123456, com.rustorio.domain.ItemShape.SQUARE));
                    }
                }
                """);

        Path alpha = modDir("alpha_uses");
        writeModJson(alpha, "alpha_uses", "1.0.0", "com.testmod.AlphaUses",
                List.of(Map.entry("zeta_provides", ">=1.0.0")));
        buildJarWithEntryPoint(alpha, "alpha_uses", "com.testmod.AlphaUses", """
                package com.testmod;
                public final class AlphaUses implements com.rustorio.api.mod.RustorioMod {
                    @Override
                    public void registerContent(com.rustorio.api.mod.RegistrationContext ctx) {
                        com.rustorio.api.content.ContentId alloyId = com.rustorio.api.content.ContentId.of("zeta_provides:alloy");
                        com.rustorio.domain.ItemType alloy = ctx.items().peek(alloyId).orElseThrow(
                                () -> new IllegalStateException("zeta's alloy not visible yet - dependency order is wrong"));
                        com.rustorio.api.content.ContentId ownId = com.rustorio.api.content.ContentId.of("alpha_uses:widget");
                        ctx.items().register(ownId, new com.rustorio.domain.ItemType(
                                ownId, "Widget", false, 0xABCDEF, com.rustorio.domain.ItemShape.TRIANGLE));
                        com.rustorio.domain.ItemType widget = ctx.items().peek(ownId).orElseThrow();
                        com.rustorio.api.content.ContentId recipeId = com.rustorio.api.content.ContentId.of("alpha_uses:widget");
                        ctx.recipes().register(recipeId, new com.rustorio.domain.Recipe(
                                recipeId, alloy, widget, 4, com.rustorio.domain.BuildingType.PRESS));
                    }
                }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(alpha, zeta));

        assertEquals(1, game.recipes().all().size());
        assertEquals("Widget", game.recipes().all().get(0).output().label());
        assertEquals("Alloy", game.recipes().all().get(0).ingredients().get(0).label());
    }

    @Test
    void aRecipeReferencingAnItemNobodyRegisteredFailsContentValidation() throws IOException {
        Path badMod = modDir("bad_mod");
        writeModJson(badMod, "bad_mod", "1.0.0", "com.testmod.BadRecipeMod", List.of());
        buildJarWithEntryPoint(badMod, "bad_mod", "com.testmod.BadRecipeMod", """
                package com.testmod;
                public final class BadRecipeMod implements com.rustorio.api.mod.RustorioMod {
                    @Override
                    public void registerContent(com.rustorio.api.mod.RegistrationContext ctx) {
                        com.rustorio.api.content.ContentId ghostId = com.rustorio.api.content.ContentId.of("bad_mod:ghost");
                        com.rustorio.domain.ItemType ghost = new com.rustorio.domain.ItemType(
                                ghostId, "Ghost", false, 0, com.rustorio.domain.ItemShape.CIRCLE);
                        com.rustorio.api.content.ContentId recipeId = com.rustorio.api.content.ContentId.of("bad_mod:ghost_recipe");
                        ctx.recipes().register(recipeId, new com.rustorio.domain.Recipe(
                                recipeId, ghost, ghost, 1, com.rustorio.domain.BuildingType.FURNACE));
                    }
                }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(badMod));

        assertEquals(1, game.skippedMods().size(),
                "мод, чей рецепт ссылается на незарегистрированный предмет, не должен попасть в игру");
        SkippedMod skipped = game.skippedMods().get(0);
        assertEquals(new ModId("bad_mod"), skipped.id(), "пропущен должен быть автор рецепта");
        assertTrue(skipped.reason().contains("bad_mod:ghost"),
                "причина должна называть висящую ссылку: " + skipped.reason());
    }

    @Test
    void loadOrderIsDeterminedByModNameNotByTheOrderDirectoriesAreHandedIn() throws IOException {
        Path aaa = modDir("aaa_mod");
        writeModJson(aaa, "aaa_mod", "1.0.0", null, List.of());
        writeItem(aaa, "raw", "Raw", "#111111", "CIRCLE");
        writeItem(aaa, "refined", "Refined", "#222222", "SQUARE");
        writeRecipe(aaa, "raw", "refined", 2, "FURNACE");

        Path zzz = modDir("zzz_mod");
        writeModJson(zzz, "zzz_mod", "1.0.0", null, List.of());
        writeItem(zzz, "raw2", "Raw2", "#333333", "CIRCLE");
        writeItem(zzz, "refined2", "Refined2", "#444444", "SQUARE");
        writeRecipe(zzz, "raw2", "refined2", 3, "FURNACE");

        LoadedGame forward = ModLoader.loadAll(List.of(aaa, zzz));
        LoadedGame reversed = ModLoader.loadAll(List.of(zzz, aaa));

        assertEquals("Refined", forward.recipes().all().get(0).output().label(), "aaa_mod's recipe must come first (name order)");
        assertEquals("Refined", reversed.recipes().all().get(0).output().label(),
                "handing the SAME directories in the opposite list order must not change the result");
        assertEquals(forward.recipes().all().stream().map(r -> r.output().label()).toList(),
                reversed.recipes().all().stream().map(r -> r.output().label()).toList());
    }

    private Path modDir(String name) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    private void writeModJson(Path modDir, String id, String version, String entryPoint, List<Map.Entry<String, String>> deps) throws IOException {
        StringBuilder json = new StringBuilder("{ \"id\": \"" + id + "\", \"version\": \"" + version + "\"");
        if (entryPoint != null) {
            json.append(", \"entryPoint\": \"").append(entryPoint).append('"');
        }
        json.append(", \"dependencies\": [");
        for (int i = 0; i < deps.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append("{ \"modId\": \"").append(deps.get(i).getKey())
                    .append("\", \"range\": \"").append(deps.get(i).getValue()).append("\" }");
        }
        json.append("] }");
        Files.writeString(modDir.resolve("mod.json"), json.toString());
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

    private void buildJarWithEntryPoint(Path modDir, String modId, String entryPointClassName, String source) {
        Path jar = modDir.resolve(modId + ".jar");
        TestModJarBuilder.build(jar,
                Map.of(entryPointClassName, source),
                Map.of("com.rustorio.api.mod.RustorioMod", entryPointClassName));
    }
}
