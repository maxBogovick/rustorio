package com.rustorio.domain.world;

import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proves the replay-hash mechanism itself, independent of any particular game balance: the
 * canonical dump ({@link WorldReplayTest#canonicalState}) plus its SHA-256 ({@link
 * WorldReplayTest#sha256}) is (1) stable across repeated computation in one JVM launch, (2) stable
 * across separate JVM launches (a hardcoded baseline, re-checked on every {@code ./gradlew test}),
 * and (3) sensitive to any change in world state — a hash that never changes would pass vacuously.
 * Reuses {@link WorldReplayTest#buildScene()} rather than building a second copy of the same
 * fixture (miner, belt, furnace, press, chest, splitter, underground belt, lab — every sealed
 * {@code Building} subtype) for a differently-scoped assertion.
 */
class WorldStateHashDeterminismTest {

    private static final int TICKS = 50;

    /**
     * Recorded baseline for {@link WorldReplayTest#buildScene()} after {@value #TICKS} ticks — far
     * fewer than {@link WorldReplayTest}'s 4000, since this test only needs the tick loop to have
     * run at all, not to have reached any particular production milestone. A mismatch here means
     * either the tick/dump path changed or {@link WorldReplayTest}'s fixture changed underneath
     * this test; either way, read the diff before updating (same rule as {@link
     * WorldReplayTest#EXPECTED_HASH}).
     *
     * <p>Updated: same reason as {@link WorldReplayTest#EXPECTED_HASH}
     * — {@code canonicalState} now prints each item's {@code ContentId} instead of the old {@code
     * Item} enum's name, a format change, not a behavior one.
     *
     * <p>Updated again: same reason as {@link WorldReplayTest#EXPECTED_HASH}'s latest entry
     * (code review finding S3) — {@code ItemType.toString()} now returns just {@code label()}
     * instead of the record's default every-field dump, changing how every {@code ItemType}-valued
     * memento field prints. Format change, not a behavior one.
     */
    private static final String EXPECTED_HASH =
            "429947a595c7e5d804ea188889a18e88432b8181cd24e0cd7ed38a2992af5254";

    @Test
    void sameSceneHashedTwiceInOneJvmLaunchProducesIdenticalHash() {
        String first = runSceneAndHash();
        String second = runSceneAndHash();

        assertEquals(first, second,
                "the same deterministic scenario computed twice in one run must hash identically");
    }

    @Test
    void hashMatchesRecordedBaselineAcrossSeparateJvmLaunches() {
        String actual = runSceneAndHash();

        assertEquals(EXPECTED_HASH, actual,
                "replay hash for a fixed scenario must not drift between JVM launches — recorded"
                        + " baseline no longer matches, actual=" + actual);
    }

    @Test
    void additionalStateChangeAfterBaselineTicksChangesTheHash() {
        WorldReplayTest.Scene scene = WorldReplayTest.buildScene();
        runTicks(scene, TICKS);
        String beforeExtraTick = WorldReplayTest.sha256(WorldReplayTest.canonicalState(scene.world()));

        // One more tick moves at least one item on a belt or through a furnace/miner cycle,
        // changing some building's memento — a hash indifferent to this would be worthless as a
        // regression net (the risk this whole card's javadoc warns about).
        runTicks(scene, 1);
        String afterExtraTick = WorldReplayTest.sha256(WorldReplayTest.canonicalState(scene.world()));

        assertNotEquals(beforeExtraTick, afterExtraTick,
                "advancing the world by one more tick must change the canonical dump's hash");
    }

    private static String runSceneAndHash() {
        WorldReplayTest.Scene scene = WorldReplayTest.buildScene();
        runTicks(scene, TICKS);
        return WorldReplayTest.sha256(WorldReplayTest.canonicalState(scene.world()));
    }

    /** Mirrors {@link WorldReplayTest}'s fuel top-up: both FURNACE-kind buildings need coal every tick. */
    private static void runTicks(WorldReplayTest.Scene scene, int ticks) {
        for (int i = 0; i < ticks; i++) {
            scene.furnace1().accept(scene.world(), VanillaItems.COAL);
            scene.furnace2().accept(scene.world(), VanillaItems.COAL);
            scene.world().tick();
        }
    }
}
