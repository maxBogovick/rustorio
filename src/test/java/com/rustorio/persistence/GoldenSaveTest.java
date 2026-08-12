package com.rustorio.persistence;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads a save file checked into the repository, written by an earlier build, and requires it to
 * still restore the same world.
 *
 * <p><b>The gap this closes.</b> {@link JsonSaveRepositoryTest} is a round trip: it saves and loads
 * with the same code, so it stays green through any format change as long as writing and reading
 * change together — which is exactly what a refactor does. A player's file on disk was written by
 * yesterday's build, and nothing in the suite read one until this test existed. The failure mode is
 * silent: rename a serialized field, reshape a {@code Codec}, forget to bump {@link
 * WorldSnapshot#CURRENT_VERSION}, and every existing save becomes unreadable while the build stays
 * green.
 *
 * <p><b>What a failure here means.</b> Two different things, and the message says which:
 * <ul>
 *   <li><i>The fixture no longer loads</i> — the save format changed. If that was intended, {@link
 *   WorldSnapshot#CURRENT_VERSION} must be bumped (this codebase's stated policy for ANY format
 *   change) and the fixture regenerated, see below. If it wasn't intended, the change just broke
 *   every existing save.</li>
 *   <li><i>It loads but restores something else</i> — the format still parses while its meaning
 *   moved, which no version check can catch. This is the case round-trip tests are structurally
 *   blind to.</li>
 * </ul>
 *
 * <p><b>Regenerating the fixture</b> is a deliberate, visible step, not something to do to make a
 * red test green: run
 * {@code GOLDEN_REGENERATE=1 ./gradlew test --tests '*GoldenSaveTest*'}, then review the diff
 * of {@code src/test/resources/saves/} — it is the format change, spelled out. The world it records
 * is built by {@link #buildFixtureWorld()} below; keep that method stable, because a fixture whose
 * contents drift with every change proves nothing.
 */
class GoldenSaveTest {

    /** Checked-in save written by an earlier build; regenerated only through the property below. */
    private static final Path FIXTURE = Path.of("src", "test", "resources", "saves", "world-v10.json");

    /**
     * An environment variable, not a system property: Gradle forks the test JVM, so a
     * {@code -D} on the Gradle command line never reaches this code, while the environment is
     * inherited.
     */
    private static final String REGENERATE_ENV = "GOLDEN_REGENERATE";

    @Test
    void aSaveWrittenByAnEarlierBuildStillRestoresTheSameWorld(@TempDir Path dir) throws IOException {
        if (System.getenv(REGENERATE_ENV) != null) {
            regenerateFixture(dir);
            return;
        }

        // Copied out of the source tree: load() reads the path it was constructed with, and a test
        // must never write to a checked-in file by accident.
        Path save = dir.resolve("world.json");
        Files.copy(FIXTURE, save, StandardCopyOption.REPLACE_EXISTING);

        World world = new World(20, 20);
        SaveResult result = new JsonSaveRepository(save).load(world);

        assertTrue(result.succeeded(),
                "the checked-in save no longer loads: the format changed. If that was intended, bump "
                        + "WorldSnapshot.CURRENT_VERSION and regenerate the fixture with "
                        + "GOLDEN_REGENERATE=1; if not, this change just broke every save on "
                        + "disk. Result: " + result);

        assertEquals(VanillaBuildings.idFor(BuildingType.MINER), protoprototypeAt(world, 6, 5), "miner moved or lost its kind");
        assertEquals(VanillaBuildings.idFor(BuildingType.FURNACE), protoprototypeAt(world, 8, 5), "furnace moved or lost its kind");
        assertEquals(VanillaBuildings.idFor(BuildingType.BELT), protoprototypeAt(world, 9, 5), "belt moved or lost its kind");
        assertEquals(VanillaBuildings.idFor(BuildingType.CHEST), protoprototypeAt(world, 7, 5), "chest moved or lost its kind");
        assertEquals(VanillaBuildings.idFor(BuildingType.LAB), protoprototypeAt(world, 14, 5), "lab moved or lost its kind");
        assertEquals(120L, world.currentTick(),
                "the world clock is part of the snapshot; a restored clock that differs puts "
                        + "timestamped production stats permanently out of step");
        assertTrue(world.research().isUnlocked(VanillaTechs.FAST_MINING),
                "an unlocked technology has to survive the round trip — the fixture used to record "
                        + "an EMPTY research set, so nothing about how technologies are written was "
                        + "actually being checked here");
    }

    /**
     * Writes the fixture from {@link #buildFixtureWorld()} through the normal save path, so the
     * checked-in file is always something the production writer actually produces — never
     * hand-edited JSON, which would test a format the game doesn't write.
     */
    private static void regenerateFixture(Path dir) throws IOException {
        Path written = dir.resolve("regenerated.json");
        World world = buildFixtureWorld();
        assertTrue(new JsonSaveRepository(written).save(world).succeeded(), "regeneration failed to save");
        Files.createDirectories(FIXTURE.getParent());
        Files.copy(written, FIXTURE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * The world the fixture records: one of each of the load-bearing building kinds at fixed
     * coordinates, a non-zero clock, and one unlocked technology. Deliberately small and
     * deliberately stable — this method changing is what makes an old fixture stop being
     * comparable, so change it only when the fixture is regenerated on purpose.
     */
    private static World buildFixtureWorld() {
        World world = new World(20, 20);
        // A real unlocked technology, so the fixture actually contains one written-out tech id.
        world.addResearchPoints(VanillaTechs.frozen().get(VanillaTechs.FAST_MINING).cost());
        world.tryUnlockTech(VanillaTechs.FAST_MINING);
        world.placeMiner(6, 5); // (6, 5) is the centre of the standard map's first iron patch
        world.placeChest(7, 5);
        world.placeFurnace(8, 5, Direction.RIGHT);
        world.placeBelt(9, 5, Direction.RIGHT);
        world.placeLab(14, 5);
        for (int tick = 0; tick < 120; tick++) {
            world.tick();
        }
        return world;
    }

    private static ContentId protoprototypeAt(World world, int x, int y) {
        return world.peek(x, y).orElseThrow().prototypeId();
    }
}
