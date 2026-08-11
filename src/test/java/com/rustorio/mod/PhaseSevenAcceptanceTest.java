package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.TransportNode;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.JsonSaveRepository;
import com.rustorio.persistence.SaveRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7's own acceptance criterion, word for word: an {@code example-mod} under {@code
 * src/test/resources/mods/} adds a new ore, a recipe for it, a data-configured building, and a
 * building backed by real code — all loaded through the actual {@link ModLoader}, not a bypass —
 * and the full cycle (place, tick, produce, save, reload) goes through end to end.
 *
 * <p>The mod's Java half ({@code javasrc/ExampleModEntryPoint.java} / {@code
 * ExampleModConveyor.java}, checked into the fixture directory as plain source) is compiled into a
 * REAL {@code .jar} at test time by {@link TestModJarBuilder} — not a fixture of pre-built
 * {@code .class} files — the same proof E7-04's own tests already established, now exercised
 * through the full lifecycle rather than in isolation.
 */
class PhaseSevenAcceptanceTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    /** The shipped example mod, in the place a modder actually looks — and continuously verified by this test rather than left to rot. */
    private static final Path EXAMPLE_MOD_SOURCE = Path.of("examples", "examplemod");

    @Test
    void newOreAndRecipeDriveARealDataConfiguredBuildingThroughARealRecipe(@TempDir Path tempDir) throws IOException {
        LoadedGame game = loadVanillaPlusExampleMod(tempDir);
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), game.recipes(), game.items(), game.buildings());
        World world = new World(4, 4, factory);

        ItemType copperOre = game.items().get(ContentId.of("examplemod:copper_ore"));
        ItemType copperIngot = game.items().get(ContentId.of("examplemod:copper_ingot"));

        Building copperFurnace = factory.create(ContentId.of("examplemod:copper_furnace"), Direction.RIGHT);
        world.restoreBuilding(0, 0, copperFurnace);
        world.restoreBuilding(1, 0, new Chest());

        assertTrue(copperFurnace.accept(world, copperOre), "the data-configured building must accept its own mod's new ore");
        // A FURNACE-archetype building needs fuel on hand before it starts a batch at all — the
        // same precondition the vanilla Furnace class already enforces, unrelated to which
        // prototype (vanilla or JSON-configured) governs this particular instance.
        assertTrue(copperFurnace.accept(world, VanillaItems.COAL), "the archetype's own fuel precondition applies here too");

        int time = game.recipes().findByOutput(BuildingType.FURNACE, copperIngot).orElseThrow().time();
        for (int i = 0; i < time; i++) {
            world.tick();
        }

        Chest sink = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, sink.amount(copperIngot),
                "a real recipe, a real new ore, and a JSON-configured building must cooperate through the actual tick loop");
    }

    @Test
    void codeModsConveyorFusesWithVanillaBeltsAndSurvivesSaveLoad(@TempDir Path tempDir) throws IOException {
        LoadedGame game = loadVanillaPlusExampleMod(tempDir);
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), game.recipes(), game.items(), game.buildings());
        World world = new World(6, 4, factory);
        ContentId conveyorId = ContentId.of("examplemod:conveyor");

        assertTrue(world.place(BuildingType.CHEST, 0, 0, Direction.RIGHT));
        assertTrue(world.place(BuildingType.BELT, 1, 0, Direction.RIGHT));
        world.restoreBuilding(2, 0, factory.create(conveyorId, Direction.RIGHT));
        assertTrue(world.place(BuildingType.BELT, 3, 0, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 4, 0, Direction.RIGHT));

        Building moddedNode = world.peek(2, 0).orElseThrow();
        assertInstanceOf(TransportNode.class, moddedNode, "the code mod's own class must actually implement the transport capability");
        assertSame(((TransportNode) world.peek(1, 0).orElseThrow()).segment(), ((TransportNode) moddedNode).segment(),
                "a REAL jar-compiled class must fuse into the same segment as the vanilla belt behind it");
        assertSame(((TransportNode) moddedNode).segment(), ((TransportNode) world.peek(3, 0).orElseThrow()).segment(),
                "and the same segment as the vanilla belt ahead of it too");

        ItemType copperOre = game.items().get(ContentId.of("examplemod:copper_ore"));
        Chest source = (Chest) world.peek(0, 0).orElseThrow();
        assertTrue(source.accept(world, copperOre));
        for (int i = 0; i < 10; i++) {
            world.tick();
        }
        Chest sink = (Chest) world.peek(4, 0).orElseThrow();
        assertEquals(1, sink.count(), "cargo must cross a real vanilla belt, then the code mod's own node, then another real vanilla belt");

        assertTrue(world.removeBuilding(2, 0).isPresent());
        assertNotSame(((TransportNode) world.peek(1, 0).orElseThrow()).segment(),
                ((TransportNode) world.peek(3, 0).orElseThrow()).segment(),
                "demolishing the code mod's tile must split the run, same as demolishing a vanilla belt tile would");

        // Save/load through the REAL JsonSaveRepository, with a mod-aware item registry — the code
        // mod's own node must survive with its modded identity and cargo intact, not fall back to
        // a vanilla Belt (the exact gap PhaseFiveAcceptanceTest's own last test closed for
        // com.examplemod's in-repo fixture; here it's proven for a genuinely external jar instead).
        World forSave = new World(4, 4, factory);
        Building nodeToSave = factory.create(conveyorId, Direction.RIGHT);
        forSave.restoreBuilding(0, 0, nodeToSave);
        assertTrue(nodeToSave.accept(forSave, copperOre));
        SaveRepository repository = new JsonSaveRepository(tempDir.resolve("conveyor-save.json"), game.items());
        assertTrue(repository.save(forSave).succeeded());

        // A second, independent mod load — a real mod's own runtime re-registers the same content
        // on every launch, so the reload path must go through ModLoader again, not reuse the first
        // LoadedGame's Java objects.
        LoadedGame reloadedGame = loadVanillaPlusExampleMod(tempDir.resolve("reload-copy"));
        BuildingFactory reloadedFactory = new BuildingFactory(PatchOreLayout.standard(), reloadedGame.recipes(), reloadedGame.items(), reloadedGame.buildings());
        World reloaded = new World(4, 4, reloadedFactory);
        assertTrue(repository.load(reloaded).succeeded());

        Building reloadedNode = reloaded.peek(0, 0).orElseThrow();
        assertEquals(conveyorId, reloadedNode.prototypeId(),
                "reload must remember the exact modded prototype id, not fall back to a vanilla BELT");
        assertEquals(Optional.of(reloadedGame.items().get(ContentId.of("examplemod:copper_ore"))), reloadedNode.heldItem(),
                "cargo in transit must survive the round trip too");
    }

    /**
     * Copies the checked-in fixture's {@code mod.json}/{@code content/} into a fresh directory
     * under {@code tempDir}, compiles its {@code javasrc/*.java} into a real {@code .jar} there
     * (via {@link TestModJarBuilder}), then loads it alongside the vanilla JSON mod through {@link
     * ModLoader#loadAll}. A fresh copy per call — never the checked-in directory itself — so
     * nothing this test does can leave a generated {@code .jar} inside version-controlled sources.
     */
    private static LoadedGame loadVanillaPlusExampleMod(Path tempDir) throws IOException {
        Path modDir = tempDir.resolve("examplemod");
        copyDirectory(EXAMPLE_MOD_SOURCE.resolve("content"), modDir.resolve("content"));
        Files.copy(EXAMPLE_MOD_SOURCE.resolve("mod.json"), modDir.resolve("mod.json"));

        String entryPointSource = Files.readString(EXAMPLE_MOD_SOURCE.resolve("javasrc").resolve("ExampleModEntryPoint.java"));
        String conveyorSource = Files.readString(EXAMPLE_MOD_SOURCE.resolve("javasrc").resolve("ExampleModConveyor.java"));
        TestModJarBuilder.build(modDir.resolve("examplemod.jar"),
                Map.of(
                        "com.examplemod.jarmod.ExampleModEntryPoint", entryPointSource,
                        "com.examplemod.jarmod.ExampleModConveyor", conveyorSource),
                Map.of("com.rustorio.api.mod.RustorioMod", "com.examplemod.jarmod.ExampleModEntryPoint"));

        return ModLoader.loadAll(java.util.List.of(RUSTORIO_MOD_DIR, modDir));
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }
}
