package com.rustorio.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.BuildingPlaceEvent;
import com.rustorio.api.mod.BuildingPlacedEvent;
import com.rustorio.api.mod.TickEvent;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.world.World;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import com.rustorio.persistence.SaveResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What has to be true about a game assembled from loaded mods, and was not: a mod's event
 * subscription actually fires while the game runs, a mod's refusal of a placement is honoured, and
 * a save survives a mod renaming one of its building prototypes.
 *
 * <p>Both mechanisms existed and had their own unit tests long before this class. Neither was
 * reachable from anything that assembles a real game: the event bus was never attached to the
 * world outside {@code EventWiringTest}, and every caller that opened a save passed the loaded item
 * registry but left the renames at their empty default. These tests exercise the assembly itself
 * rather than the mechanisms, which is the gap that let both slip through.
 *
 * <p>Loads the REAL vanilla mod alongside the temporary one on purpose: {@code World}'s starting
 * inventory is denominated in vanilla items, so a registry without them cannot round-trip a save
 * at all — and a mod sitting next to vanilla is what actually happens on a player's disk.
 */
class GameBootstrapTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final int WIDTH = 12;
    private static final int HEIGHT = 8;

    @TempDir
    Path tempDir;

    @Test
    void aModSubscribedToBuildingPlacedHearsAboutABuildingPlacedInTheAssembledWorld() throws IOException {
        Path mod = writeBoxMod("boxmod", "1.0.0", "plain_box", "");
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));
        List<BuildingPlacedEvent> heard = new ArrayList<>();
        content.events().subscribe(BuildingPlacedEvent.class, heard::add);

        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(), WIDTH, HEIGHT);
        assertTrue(world.place(ContentId.of("boxmod:plain_box"), 3, 3, Direction.RIGHT),
                "фикстура: здание должно вставать на пустую клетку, иначе тест ниже проверяет не то");

        assertEquals(List.of(new BuildingPlacedEvent(ContentId.of("boxmod:plain_box"), 3, 3)), heard,
                "мод, подписанный на постановку здания, должен получить событие из собранной игры");
    }

    @Test
    void aModSubscribedToTickHearsAboutTicksInTheAssembledWorld() throws IOException {
        Path mod = writeBoxMod("tickmod", "1.0.0", "plain_box", "");
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));
        List<Long> ticksHeard = new ArrayList<>();
        content.events().subscribe(TickEvent.class, event -> ticksHeard.add(event.tickCount()));

        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(), WIDTH, HEIGHT);
        world.tick();
        world.tick();

        assertEquals(List.of(1L, 2L), ticksHeard, "каждый тик собранного мира должен доехать до мода");
    }

    @Test
    void aModCanRefuseAPlacementAndTheWorldIsLeftUntouched() throws IOException {
        Path mod = writeBoxMod("vetomod", "1.0.0", "plain_box", "");
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, mod));
        // Refuses column 5 and nothing else, so the same test proves both halves: the refusal and
        // that it doesn't refuse everything.
        content.events().subscribe(BuildingPlaceEvent.class, event -> {
            if (event.x() == 5) {
                event.cancel();
            }
        });
        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(), WIDTH, HEIGHT);
        ContentId box = ContentId.of("vetomod:plain_box");

        assertFalse(world.place(box, 5, 3, Direction.RIGHT), "мод отменил постановку — она обязана не состояться");
        assertTrue(world.peek(5, 3).isEmpty(), "отменённая постановка не должна оставить здание на клетке");
        assertTrue(world.place(box, 6, 3, Direction.RIGHT), "на клетке, которую мод не трогал, постановка должна пройти");
    }

    @Test
    void aSaveSurvivesTheModRenamingItsBuildingPrototype() throws IOException {
        Path savePath = tempDir.resolve("renamed.json");

        Path before = writeBoxMod("renamer", "1.0.0", "old_box", "");
        LoadedGame contentBefore = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, before));
        World worldBefore = GameBootstrap.createWorld(contentBefore, PatchOreLayout.standard(), WIDTH, HEIGHT);
        assertTrue(worldBefore.place(ContentId.of("renamer:old_box"), 4, 2, Direction.RIGHT),
                "фикстура: здание должно встать до сохранения");
        assertInstanceOf(SaveResult.Success.class, GameBootstrap.saves(contentBefore, savePath).save(worldBefore),
                "фикстура: сохранение должно пройти, иначе загрузка ниже проверяет не то");

        Path after = writeBoxMod("renamer", "2.0.0", "new_box",
                ", \"renames\": { \"renamer:old_box\": \"renamer:new_box\" }");
        LoadedGame contentAfter = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, after));
        World worldAfter = GameBootstrap.createWorld(contentAfter, PatchOreLayout.standard(), WIDTH, HEIGHT);

        SaveResult result = GameBootstrap.saves(contentAfter, savePath).load(worldAfter);

        assertInstanceOf(SaveResult.Success.class, result,
                "переименованный прототип — не пропавший контент: загрузка должна быть полной, не частичной");
        assertEquals(ContentId.of("renamer:new_box"), worldAfter.peek(4, 2).orElseThrow().prototypeId(),
                "здание должно вернуться под новым именем на ту же клетку");
    }

    /**
     * A one-building, one-item mod under its own namespace. {@code extraModJson} is spliced into
     * {@code mod.json} after the version — the only per-test difference is whether it declares a
     * rename, so building the rest twice would only invite the two copies to drift apart.
     */
    private Path writeBoxMod(String modId, String version, String buildingPath, String extraModJson)
            throws IOException {
        Path dir = tempDir.resolve(modId + "-" + version);
        Files.createDirectories(dir.resolve("content").resolve("buildings"));
        Files.writeString(dir.resolve("mod.json"), """
                { "id": "%s", "version": "%s", "dependencies": [{ "modId": "rustorio", "range": ">=1.0.0" }]%s }
                """.formatted(modId, version, extraModJson));
        // CHEST archetype and placement ALWAYS: the point under test is the assembly, so the
        // building must not also depend on terrain, ore or a recipe being right.
        Files.writeString(dir.resolve("content").resolve("buildings").resolve(buildingPath + ".json"), """
                {
                  "path": "%s",
                  "label": "Box",
                  "archetype": "CHEST",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "ALWAYS",
                  "texture": "rustorio:chest"
                }
                """.formatted(buildingPath));
        return dir;
    }
}
