package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.TechType;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Research used to be the one part of the game a mod could not touch at all: technologies were
 * {@code enum} constants, and no mod can add one of those. A mod could not add a technology, ask
 * whether one was unlocked, or price one.
 *
 * <p>What a technology DOES is still the game's own code — these tests deliberately assert only
 * that a mod's technology exists, costs points, respects prerequisites and unlocks, which is the
 * whole of what data can express today.
 */
class ModTechTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");

    @TempDir
    Path tempDir;

    @Test
    void aJsonOnlyModAddsItsOwnTechnologyToTheTree() throws IOException {
        Path mod = writeTechMod("""
                { "path": "cheap_trick", "label": "Cheap trick", "cost": 10 }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));

        TechType added = game.techs().get(ContentId.of("techmod:cheap_trick"));
        assertEquals("Cheap trick", added.label());
        assertEquals(10, added.cost());
        assertEquals(6, game.techs().size(), "пять ванильных технологий плюс одна модовая");
    }

    @Test
    void aModsOwnTechnologyIsUnlockedThroughTheSamePointsAndPrerequisites() throws IOException {
        Path mod = writeTechMod("""
                { "path": "cheap_trick", "label": "Cheap trick", "cost": 10,
                  "prerequisites": ["rustorio:fast_mining"] }
                """);
        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));
        World world = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 12, 8);
        ContentId trick = ContentId.of("techmod:cheap_trick");

        world.addResearchPoints(10);
        assertFalse(world.tryUnlockTech(trick), "предпосылка не открыта — открывать нечего");

        world.addResearchPoints(game.techs().get(ContentId.of("rustorio:fast_mining")).cost());
        assertTrue(world.tryUnlockTech(ContentId.of("rustorio:fast_mining")));
        assertTrue(world.tryUnlockTech(trick), "предпосылка открыта и очки есть — технология мода должна открыться");
        assertTrue(world.research().isUnlocked(trick));
    }

    @Test
    void aTechnologyRequiringSomethingNobodyRegisteredSkipsThatMod() throws IOException {
        Path mod = writeTechMod("""
                { "path": "cheap_trick", "label": "Cheap trick", "cost": 10,
                  "prerequisites": ["rustorio:no_such_tech"] }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));

        assertEquals(List.of(new ModId("techmod")), game.skippedMods().stream().map(SkippedMod::id).toList());
        assertTrue(game.skippedMods().get(0).reason().contains("rustorio:no_such_tech"),
                game.skippedMods().get(0).reason());
    }

    @Test
    void twoTechnologiesRequiringEachOtherSkipTheModThatWroteThem() throws IOException {
        Path mod = tempDir.resolve("techmod");
        Files.createDirectories(mod.resolve("content").resolve("techs"));
        Files.writeString(mod.resolve("mod.json"), """
                { "id": "techmod", "version": "1.0.0", "dependencies": [{ "modId": "rustorio", "range": ">=1.0.0" }] }
                """);
        Files.writeString(mod.resolve("content").resolve("techs").resolve("a.json"), """
                { "path": "a", "label": "A", "cost": 10, "prerequisites": ["b"] }
                """);
        Files.writeString(mod.resolve("content").resolve("techs").resolve("b.json"), """
                { "path": "b", "label": "B", "cost": 10, "prerequisites": ["a"] }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));

        assertEquals(List.of(new ModId("techmod")), game.skippedMods().stream().map(SkippedMod::id).toList());
        assertTrue(game.skippedMods().get(0).reason().contains("prerequisite"),
                "цикл был невозможен, пока технологии были константами enum'а, и теперь его ловит загрузчик: "
                        + game.skippedMods().get(0).reason());
    }

    @Test
    void aTechnologyListingAnUnknownEffectSkipsThatMod() throws IOException {
        Path mod = writeTechMod("""
                { "path": "cheap_trick", "label": "Cheap trick", "cost": 10,
                  "effects": ["no_such_effect"] }
                """);

        LoadedGame game = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));

        assertEquals(List.of(new ModId("techmod")), game.skippedMods().stream().map(SkippedMod::id).toList());
        assertTrue(game.skippedMods().get(0).reason().contains("no_such_effect"),
                game.skippedMods().get(0).reason());
    }

    private Path writeTechMod(String techJson) throws IOException {
        Path dir = tempDir.resolve("techmod");
        Files.createDirectories(dir.resolve("content").resolve("techs"));
        Files.writeString(dir.resolve("mod.json"), """
                { "id": "techmod", "version": "1.0.0", "dependencies": [{ "modId": "rustorio", "range": ">=1.0.0" }] }
                """);
        Files.writeString(dir.resolve("content").resolve("techs").resolve("cheap_trick.json"), techJson);
        return dir;
    }
}
