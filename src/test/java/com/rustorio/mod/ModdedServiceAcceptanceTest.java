package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.WorldServices;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine's own definition of "moddable", as one runnable claim: a mod that ships nothing but a
 * folder — its {@code mod.json}, its content JSON and its own compiled {@code .jar} — can add a
 * building with behavior the engine has never seen, backed by a CAPABILITY the engine has never
 * heard of, and have that building live, tick, produce, persist, reload, and disappear cleanly when
 * the mod is uninstalled.
 *
 * <p>What each half actually pins down, and what used to be true instead:
 *
 * <ul>
 *   <li><b>The service.</b> A mod's capability used to have to be constructed by the game's own
 *       startup code and passed into the {@code World} constructor by its concrete type — so a mod
 *       could not supply one at all without an edit to {@code GameScreen}. {@code
 *       RegistrationContext.registerService} is how it supplies its own now, and this test is what
 *       proves the engine actually delivers it: the generator produces NOTHING without it.
 *   <li><b>The panel.</b> What a building shows used to be decided by a chain of {@code instanceof}
 *       inside the renderer, so a mod's archetype could only be described by editing the engine.
 *       Here the fixture's own {@link InspectableBuilding} answers, read through the same call the
 *       renderer makes.
 *   <li><b>Uninstalling.</b> A save naming content that is no longer installed must be reported,
 *       not crash — the engine already had {@code prototypeOrUnknown} for this, and nothing until
 *       now exercised it for a mod carrying real Java behavior.
 * </ul>
 *
 * <p>The fixture is compiled into a genuine {@code .jar} at test time ({@link TestModJarBuilder}) —
 * not a directory of loose classes and not a class on this test's own classpath. That distinction
 * is the entire point: a class the test could import itself would prove nothing about what a
 * third-party mod can do.
 */
class ModdedServiceAcceptanceTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path SERVICE_MOD_SOURCE = Path.of("examples", "servicemod");
    private static final ContentId GENERATOR_ID = ContentId.of("servicemod:pulse_generator");
    private static final ContentId PULSE_ITEM_ID = ContentId.of("servicemod:pulse");

    /** The fixture pulses once every 5 asks; 40 ticks is comfortably several pulses without being so long that a stuck generator still passes. */
    private static final int TICKS = 40;

    @TempDir
    Path tempDir;

    @Test
    void aJarModsOwnBuildingRunsOnAServiceThatSameModRegistered() throws IOException {
        LoadedGame game = loadVanillaPlusServiceMod(tempDir);
        World world = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8);
        world.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        world.placeChest(1, 0);
        Chest chest = (Chest) world.peek(1, 0).orElseThrow();

        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        ItemType pulse = game.items().get(PULSE_ITEM_ID);
        assertTrue(chest.amount(pulse) > 0,
                "the mod's building must actually produce through the mod's own service — "
                        + "nothing in the engine names either, so a zero here means the service never arrived");
    }

    /**
     * The same world built with no services at all: the generator is placed and ticks happily, and
     * produces nothing. This is what makes the test above a claim about the SERVICE rather than
     * about the building — without this pair, a generator that ignored the service entirely and
     * minted on a timer would pass just as well.
     */
    @Test
    void theSameBuildingProducesNothingWhenItsServiceIsAbsent() throws IOException {
        LoadedGame game = loadVanillaPlusServiceMod(tempDir);
        World world = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8,
                game.buildings(), WorldServices.NONE);
        world.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        world.placeChest(1, 0);
        Chest chest = (Chest) world.peek(1, 0).orElseThrow();

        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        assertEquals(0, chest.count(),
                "with no service registered the mod's building must idle, not throw and not invent output");
    }

    /**
     * What the inspection panel would draw, read through the exact call the renderer makes. Asserts
     * on the fixture's own wording, which exists nowhere in the engine — if this passes, the panel
     * genuinely got its text from the mod rather than from a branch that knows this archetype.
     */
    @Test
    void theModsOwnBuildingDescribesItselfToThePanelWithNoEngineBranch() throws IOException {
        LoadedGame game = loadVanillaPlusServiceMod(tempDir);
        World world = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8);
        world.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        world.placeChest(1, 0);
        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }

        Building generator = world.peek(0, 0).orElseThrow();
        List<String> details = ((InspectableBuilding) generator).inspectionDetails(world, 0, 0);

        assertTrue(details.stream().anyMatch(line -> line.startsWith("Pulses minted: ")),
                "the panel line must come from the mod's own building: " + details);
        assertTrue(details.contains("Pulse service: ready"),
                "the building must see its own service through TickContext.service: " + details);
    }

    /**
     * The generator's own lifetime counter survives a save/load round trip through the engine's
     * generic codec path — proof that a mod's archetype needs no engine-side persistence code, only
     * a {@code Codec} of plain JDK types.
     */
    @Test
    void theModsOwnBuildingStateSurvivesSaveAndReload() throws IOException {
        LoadedGame game = loadVanillaPlusServiceMod(tempDir);
        World world = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8);
        world.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        world.placeChest(1, 0);
        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }
        int producedBefore = producedCount(world);
        assertNotEquals(0, producedBefore, "nothing to prove about persistence if nothing was produced first");

        Path saveFile = tempDir.resolve("save.json");
        SaveRepository repository = GameBootstrap.saves(game, saveFile);
        assertTrue(repository.save(world).succeeded(), "saving a world holding a modded building must succeed");

        World reloaded = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8);
        assertInstanceOf(SaveResult.Success.class, repository.load(reloaded),
                "with the mod still installed the reload must be a clean Success, not a PartialSuccess");

        Building restored = reloaded.peek(0, 0).orElseThrow();
        assertEquals(GENERATOR_ID, restored.prototypeId(),
                "reload must restore the mod's own prototype, not fall back to a vanilla one");
        assertEquals(producedBefore, producedCount(reloaded),
                "the archetype's own saved counter must come back, not reset to zero");
    }

    /**
     * The uninstall case: the same save, loaded into a world built from vanilla content only. The
     * engine must report the missing content rather than throwing — a player who removes a mod gets
     * a message, not a stack trace.
     */
    @Test
    void aSaveNamingAnUninstalledModsBuildingIsReportedRatherThanCrashing() throws IOException {
        LoadedGame withMod = loadVanillaPlusServiceMod(tempDir);
        World world = GameBootstrap.createWorld(withMod, PatchOreLayout.standard(), 8, 8);
        world.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        for (int i = 0; i < TICKS; i++) {
            world.tick();
        }
        Path saveFile = tempDir.resolve("orphaned-save.json");
        assertTrue(GameBootstrap.saves(withMod, saveFile).save(world).succeeded());

        LoadedGame vanillaOnly = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR));
        World vanillaWorld = GameBootstrap.createWorld(vanillaOnly, PatchOreLayout.standard(), 8, 8);
        SaveResult result = GameBootstrap.saves(vanillaOnly, saveFile).load(vanillaWorld);

        SaveResult.Failure failure = assertInstanceOf(SaveResult.Failure.class, result,
                "an uninstalled mod's content must be reported, not crash the loader");
        assertTrue(String.valueOf(failure.reason()).contains(PULSE_ITEM_ID.toString()),
                "the refusal must name what actually went missing, so a player can tell which mod to reinstall: "
                        + failure.reason());
        assertTrue(vanillaWorld.peek(0, 0).isEmpty(),
                "a refused load must leave the caller's world untouched, not half-restored");
    }

    /**
     * The live crash this arrangement had to be redesigned to prevent, reproduced exactly: a player
     * leaves to the main menu and starts a second game. Both worlds come from the SAME loaded game,
     * and the first one's shutdown must not reach into the second.
     *
     * <p>Before services became per-world, the mod's thread pool was one shared object: disposing
     * the first screen closed it, and the second game threw {@code RejectedExecutionException} out
     * of the very first {@code World.tick()} — from inside the mod's building, in the tick loop, on
     * a path no test touched because every test built exactly one world.
     */
    @Test
    void aSecondWorldFromTheSameLoadedGameStillWorksAfterTheFirstIsClosed() throws IOException {
        LoadedGame game = loadVanillaPlusServiceMod(tempDir);

        World first = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8);
        first.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        first.tick();
        first.closeServices(); // what GameScreen.dispose() does when the player leaves to the menu

        World second = GameBootstrap.createWorld(game, PatchOreLayout.standard(), 8, 8);
        second.place(GENERATOR_ID, 0, 0, Direction.RIGHT);
        second.placeChest(1, 0);
        for (int i = 0; i < TICKS; i++) {
            second.tick(); // must not throw: this world's services are its own
        }

        Chest chest = (Chest) second.peek(1, 0).orElseThrow();
        assertTrue(chest.amount(game.items().get(PULSE_ITEM_ID)) > 0,
                "the second game must produce normally — its service is a fresh instance, not the closed one");
    }

    /** The mod's own lifetime counter, read back off whichever world holds it — the fixture exposes it only through the panel, which is the same door the renderer uses. */
    private static int producedCount(World world) {
        Building generator = world.peek(0, 0).orElseThrow();
        String line = ((InspectableBuilding) generator).inspectionDetails(world, 0, 0).stream()
                .filter(text -> text.startsWith("Pulses minted: "))
                .findFirst()
                .orElseThrow();
        return Integer.parseInt(line.substring("Pulses minted: ".length()));
    }

    /**
     * A fresh copy of the checked-in fixture under {@code tempDir}, with its {@code javasrc/}
     * compiled into a real {@code .jar} beside its own {@code mod.json} — never the checked-in
     * directory itself, so nothing here can leave a build artifact in version-controlled sources.
     * Same shape {@code PhaseSevenAcceptanceTest} already uses for its own jar fixture.
     */
    private static LoadedGame loadVanillaPlusServiceMod(Path tempDir) throws IOException {
        Path modDir = tempDir.resolve("servicemod");
        copyDirectory(SERVICE_MOD_SOURCE.resolve("content"), modDir.resolve("content"));
        Files.copy(SERVICE_MOD_SOURCE.resolve("mod.json"), modDir.resolve("mod.json"));

        Path sources = SERVICE_MOD_SOURCE.resolve("javasrc");
        TestModJarBuilder.build(modDir.resolve("servicemod.jar"),
                Map.of(
                        "com.servicemod.jarmod.ServiceModEntryPoint",
                        Files.readString(sources.resolve("ServiceModEntryPoint.java")),
                        "com.servicemod.jarmod.PulseGenerator",
                        Files.readString(sources.resolve("PulseGenerator.java")),
                        "com.servicemod.jarmod.PulseService",
                        Files.readString(sources.resolve("PulseService.java"))),
                Map.of("com.rustorio.api.mod.RustorioMod", "com.servicemod.jarmod.ServiceModEntryPoint"));

        return ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, modDir));
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
