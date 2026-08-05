package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One broken mod must not take the game down with it. Before this, any failure anywhere in loading
 * — a typo in one mod's JSON, one mod's entry point throwing — propagated out of the loader, out of
 * the screen's constructor, and out of the process: a player with ten mods got a stack trace
 * instead of a game, with no indication which of the ten to remove.
 *
 * <p>The other half of the contract is what stays fatal. A failure that names several mods and has
 * no innocent choice among them (two directories claiming one id) must not be resolved by the
 * loader picking a loser on the player's behalf.
 */
class ModLoaderSkipTest {

    @TempDir
    Path tempDir;

    @Test
    void aModWithBrokenContentIsSkippedWhileItsHealthyNeighbourLoads() throws IOException {
        Path good = modDir("good_mod");
        writeModJson(good, "good_mod", "1.0.0", null, List.of());
        writeItem(good, "gadget", "#112233", "SQUARE");

        Path broken = modDir("broken_mod");
        writeModJson(broken, "broken_mod", "1.0.0", null, List.of());
        // "PENTAGON" is not an ItemShape — the single most ordinary kind of typo a data mod makes.
        writeItem(broken, "gizmo", "#445566", "PENTAGON");

        LoadedGame game = ModLoader.loadAll(List.of(good, broken));

        assertTrue(game.items().peek(ContentId.of("good_mod:gadget")).isPresent(),
                "исправный мод должен загрузиться, даже если сосед сломан");
        assertFalse(game.items().peek(ContentId.of("broken_mod:gizmo")).isPresent(),
                "контент пропущенного мода не должен просочиться в игру");
        assertEquals(List.of(new ModId("broken_mod")), game.skippedMods().stream().map(SkippedMod::id).toList());
        assertTrue(game.skippedMods().get(0).reason().contains("PENTAGON"),
                "причина должна называть само неверное значение: " + game.skippedMods().get(0).reason());
    }

    @Test
    void aModThatDependedOnASkippedModIsSkippedToo() throws IOException {
        Path broken = modDir("broken_mod");
        writeModJson(broken, "broken_mod", "1.0.0", null, List.of());
        writeItem(broken, "gizmo", "#445566", "PENTAGON");

        Path dependent = modDir("dependent_mod");
        writeModJson(dependent, "dependent_mod", "1.0.0", null, List.of(Map.entry("broken_mod", ">=1.0.0")));
        writeItem(dependent, "widget", "#778899", "CIRCLE");

        LoadedGame game = ModLoader.loadAll(List.of(broken, dependent));

        assertEquals(List.of(new ModId("broken_mod"), new ModId("dependent_mod")),
                game.skippedMods().stream().map(SkippedMod::id).toList(),
                "мод, которому нужен пропущенный, тоже не может работать — и пропускается вторым, после него");
        assertFalse(game.items().peek(ContentId.of("dependent_mod:widget")).isPresent(),
                "контент каскадно пропущенного мода тоже не должен просочиться");
    }

    @Test
    void aModWhoseJsonIsUnreadableIsSkippedByItsDirectoryNameWithoutSpinningForever() throws IOException {
        Path good = modDir("good_mod");
        writeModJson(good, "good_mod", "1.0.0", null, List.of());
        writeItem(good, "gadget", "#112233", "SQUARE");

        // Its own id is exactly what can't be read here, so the only identity left is the folder —
        // and re-reading the same unreadable file on the next attempt is how this used to loop.
        Path unreadable = modDir("unreadable_mod");
        Files.writeString(unreadable.resolve("mod.json"), "{ this is not json");

        LoadedGame game = ModLoader.loadAll(List.of(good, unreadable));

        assertEquals(List.of(new ModId("unreadable_mod")), game.skippedMods().stream().map(SkippedMod::id).toList());
        assertTrue(game.items().peek(ContentId.of("good_mod:gadget")).isPresent(),
                "исправный мод должен загрузиться рядом с нечитаемым");
    }

    @Test
    void twoDirectoriesClaimingOneModIdStayFatalBecauseNeitherIsTheObviousLoser() throws IOException {
        Path first = modDir("copy_one");
        writeModJson(first, "same_id", "1.0.0", null, List.of());
        Path second = modDir("copy_two");
        writeModJson(second, "same_id", "2.0.0", null, List.of());

        ModLoadException thrown =
                assertThrows(ModLoadException.class, () -> ModLoader.loadAll(List.of(first, second)));

        assertTrue(thrown.getMessage().contains("same_id"), thrown.getMessage());
        assertEquals(null, thrown.culprit(),
                "виновника нет: выбор одной из двух копий за игрока — не работа загрузчика");
    }

    private Path modDir(String name) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    private void writeModJson(Path modDir, String id, String version, String entryPoint,
            List<Map.Entry<String, String>> deps) throws IOException {
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

    private void writeItem(Path modDir, String path, String colorRgb, String shape) throws IOException {
        Path itemsDir = modDir.resolve("content").resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve(path + ".json"), """
                { "path": "%s", "label": "%s", "colorRgb": "%s", "shape": "%s" }
                """.formatted(path, path, colorRgb, shape));
    }
}
