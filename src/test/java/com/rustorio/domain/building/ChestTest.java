package com.rustorio.domain.building;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.VanillaTechs;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Chest}: accumulates whatever it's handed, badge tracks the count — see P2-04. Since D-02
 * (DEV_TASKS.md, §2.5 of the design audit) it's a real buffer, not an incinerator: item identity
 * survives ({@link #tracksEachItemKindSeparately}), storage is capped ({@link
 * #refusesNewItemsOnceAtCapacity}, {@link #bigBufferDoublesTheCapacity}), and it actively pushes
 * content back out ({@link #pushesOneStoredItemPerTickOutThroughItsDirection}).
 */
class ChestTest {

    @Test
    void emptyChestHasNoBadge() {
        Chest chest = new Chest();

        assertEquals(VanillaSprites.CHEST, chest.appearance().sprite());
        assertFalse(chest.appearance().hasBadge(), "an empty chest must not draw a \"0\" badge");
    }

    @Test
    void filledChestShowsItsCount() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        chest.accept(world, VanillaItems.IRON_ORE);
        chest.accept(world, VanillaItems.IRON_ORE);

        assertTrue(chest.appearance().hasBadge());
        assertEquals(2, chest.appearance().badge());
    }

    @Test
    void chestAcceptsAnyItem() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        for (ItemType item : VanillaItems.frozen().iterate()) {
            assertTrue(chest.accept(world, item), "a chest must accept every item kind, unsorted");
        }
        assertEquals(VanillaItems.frozen().size(), chest.count());
    }

    /** (D-02, DEV_TASKS.md) §2.5's actual defect: item TYPE used to be thrown away on accept. */
    @Test
    void tracksEachItemKindSeparately() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        chest.accept(world, VanillaItems.IRON_ORE);
        chest.accept(world, VanillaItems.IRON_ORE);
        chest.accept(world, VanillaItems.BRONZE_ORE);

        assertEquals(2, chest.amount(VanillaItems.IRON_ORE));
        assertEquals(1, chest.amount(VanillaItems.BRONZE_ORE));
        assertEquals(0, chest.amount(VanillaItems.GEAR), "never offered any GEAR — must read back as zero, not stay unset oddly");
        assertEquals(3, chest.count(), "the badge total is still the sum across every kind");
    }

    private static final int CAPACITY = 100; // mirrors Chest.CAPACITY (private constant)

    @Test
    void refusesNewItemsOnceAtCapacity() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, VanillaItems.IRON_ORE), "unit " + i + " should still fit under the cap");
        }

        assertFalse(chest.accept(world, VanillaItems.IRON_ORE), "the chest is full — one more must be refused");
        assertFalse(chest.accept(world, VanillaItems.BRONZE_ORE), "full is full regardless of which kind is offered next");
        assertEquals(CAPACITY, chest.count());
    }

    /** (D-02, DEV_TASKS.md) §2.5: "тех BIG_BUFFER к ящику не применяется, хотя по названию должен." */
    @Test
    void bigBufferDoublesTheCapacity() {
        World world = new World(4, 4);
        world.addResearchPoints(costOf(VanillaTechs.FAST_MINING));
        assertTrue(world.tryUnlockTech(VanillaTechs.FAST_MINING));
        world.addResearchPoints(costOf(VanillaTechs.FAST_SMELTING));
        assertTrue(world.tryUnlockTech(VanillaTechs.FAST_SMELTING));
        world.addResearchPoints(costOf(VanillaTechs.BIG_BUFFER));
        assertTrue(world.tryUnlockTech(VanillaTechs.BIG_BUFFER));
        Chest chest = new Chest();

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, VanillaItems.IRON_ORE));
        }
        assertTrue(chest.accept(world, VanillaItems.IRON_ORE), "BIG_BUFFER must have doubled the cap past the un-teched " + CAPACITY);
        assertEquals(CAPACITY + 1, chest.count());
    }

    /**
     * Without {@link SettlesEachTick}, descending pass ticks the upstream (higher-x) chest first,
     * fills its LEFT neighbor, and that neighbor then pushes in the same world tick — multi-hop.
     * One cell per tick is the same invariant belts and splitters already keep.
     */
    @Test
    void aLeftFacingChestChainMovesOnlyOneCellPerWorldTick() {
        World world = new World(5, 3);
        world.restoreBuilding(3, 1, new Chest(Direction.LEFT));
        world.restoreBuilding(2, 1, new Chest(Direction.LEFT));
        world.restoreBuilding(1, 1, new Chest(Direction.LEFT));
        Chest source = (Chest) world.peek(3, 1).orElseThrow();
        source.accept(world, VanillaItems.GEAR);

        world.tick();

        assertEquals(0, ((Chest) world.peek(3, 1).orElseThrow()).count(), "source emptied");
        assertEquals(1, ((Chest) world.peek(2, 1).orElseThrow()).count(),
                "item must stop in the middle chest this tick, not chain further left");
        assertEquals(0, ((Chest) world.peek(1, 1).orElseThrow()).count(),
                "sink must still be empty after one tick");
    }

    /**
     * Sustained LEFT chest chain must match RIGHT throughput: with {@link Chest#prefersDescendingTick}
     * matching {@link Belt}, settle marks only block true multi-hop, not every other hand-off.
     */
    @Test
    void aSustainedLeftChestChainKeepsFullThroughput() {
        int left = sustainedChestChainDelivered(Direction.LEFT);
        int right = sustainedChestChainDelivered(Direction.RIGHT);

        assertEquals(right, left,
                "LEFT must match RIGHT; half means settle marks fire on every hand-off in the descending pass");
        assertTrue(left >= 90,
                "100 ticks of a full source should deliver nearly one item per tick after the pipeline fills: "
                        + left);
    }

    private static int sustainedChestChainDelivered(Direction facing) {
        World world = new World(5, 3);
        int sourceX = facing == Direction.LEFT ? 3 : 1;
        int middleX = 2;
        int sinkX = facing == Direction.LEFT ? 1 : 3;
        world.restoreBuilding(sourceX, 1, new Chest(facing));
        world.restoreBuilding(middleX, 1, new Chest(facing));
        Chest sink = new Chest(facing);
        world.restoreBuilding(sinkX, 1, sink);
        Chest source = (Chest) world.peek(sourceX, 1).orElseThrow();

        int delivered = 0;
        for (int tick = 0; tick < 100; tick++) {
            while (source.count() < 10) {
                assertTrue(source.accept(world, VanillaItems.GEAR));
            }
            world.tick();
            delivered += sink.drain().values().stream().mapToInt(Integer::intValue).sum();
        }
        return delivered;
    }

    /** A chest is a buffer now, not a sink — it must give items back out through its facing. */
    @Test
    void pushesOneStoredItemPerTickOutThroughItsDirection() {
        World world = new World(4, 4);
        world.restoreBuilding(1, 1, new Chest(Direction.RIGHT));
        Chest sink = new Chest(Direction.LEFT); // faces off the map — a passive receiver for this assertion
        world.restoreBuilding(2, 1, sink);
        Chest source = (Chest) world.peek(1, 1).orElseThrow();
        source.accept(world, VanillaItems.GEAR);

        world.tick();

        assertEquals(0, source.count(), "the item must have left the source chest");
        assertEquals(1, sink.amount(VanillaItems.GEAR), "…and arrived at the neighbor in its output direction");
    }

    /**
     * (E5-05, owner decision) {@code speedLevel} must still yield exactly 2^N real ticks per world
     * tick — preserved from the old decorator's stacking multiplier, not weakened to a linear
     * (1+N). At {@code speedLevel} 2 that's 4 pushes in the single world tick this test runs,
     * instead of the 1 an unupgraded chest manages (see {@link #pushesOneStoredItemPerTickOutThroughItsDirection}).
     */
    @Test
    void speedLevelTwoPushesFourItemsInASingleWorldTick() {
        World world = new World(4, 4);
        Chest source = new Chest(Direction.RIGHT);
        for (int i = 0; i < 5; i++) {
            source.accept(world, VanillaItems.GEAR);
        }
        world.restoreBuilding(1, 1, source.withSpeedLevel(2));
        Chest sink = new Chest(Direction.LEFT); // faces off the map — a passive receiver for this assertion
        world.restoreBuilding(2, 1, sink);
        Chest sped = (Chest) world.peek(1, 1).orElseThrow();

        world.tick();

        assertEquals(1, sped.count(), "5 stocked minus 4 pushed in this one tick");
        assertEquals(4, sink.amount(VanillaItems.GEAR));
        assertEquals(2, sped.speedLevel());
    }

    @Test
    void rotatingChangesDirectionButKeepsContents() {
        World world = new World(4, 4);
        Chest chest = new Chest(Direction.RIGHT);
        chest.accept(world, VanillaItems.GEAR);

        Chest rotated = (Chest) chest.rotatedClockwise().orElseThrow();

        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(1, rotated.amount(VanillaItems.GEAR), "rotating must not drop what was already stored");
    }

    /**
     * (Code review finding, CODE_REVIEW_2026-07-28.md) {@code rotatedClockwise} used to always
     * build the new instance at the default {@code WORKING} status, regardless of what the chest's
     * real, just-computed status was — a rotation doesn't create a new logical chest, so an
     * actually-blocked chest must not flash back to {@code WORKING} for one tick just because the
     * player rotated it. {@code World.statusCounts()} (read directly by {@code HudRenderer.alerts()})
     * would otherwise mistally it for that tick.
     */
    @Test
    void rotatingAnOutputFullChestKeepsItsStatus() {
        World world = new World(4, 4);
        Chest chest = new Chest(Direction.RIGHT); // faces off the map — nothing ever drains it
        world.restoreBuilding(1, 1, chest);
        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, VanillaItems.IRON_ORE));
        }
        world.tick();
        assertEquals(BuildingStatus.OUTPUT_FULL, chest.appearance().status(), "sanity check before rotating");

        Chest rotated = (Chest) chest.rotatedClockwise().orElseThrow();

        assertEquals(BuildingStatus.OUTPUT_FULL, rotated.appearance().status(),
                "rotating must not silently reset an actually-blocked chest back to WORKING");
    }

    /** (F-01, DEV_TASKS.md, §2.5 of the audit's own "заполненный ящик — тоже OUTPUT_FULL" note) */
    @Test
    void chestReportsOutputFullStatusOnceAtCapacity() {
        World world = new World(4, 4);
        Chest chest = new Chest(Direction.RIGHT); // faces off the map — nothing ever drains it
        world.restoreBuilding(1, 1, chest);

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, VanillaItems.IRON_ORE));
        }
        assertEquals(BuildingStatus.WORKING, chest.appearance().status(),
                "status is only recomputed in tick() — still stale WORKING right after accept() alone");

        world.tick();

        assertEquals(BuildingStatus.OUTPUT_FULL, chest.appearance().status());
    }

    /**
     * "Full" is only worth reporting while the chest is actually STUCK (N6, NEW_BUGS_PROGRESS.md).
     * The status used to be computed on the first line of {@code tick()}, before the outgoing
     * push was even attempted — so a full chest that successfully handed an item on and dropped to
     * 99/100 still rendered as blocked for the rest of the frame.
     */
    @Test
    void aFullChestThatSuccessfullyPushesAnItemOutIsNotReportedAsBlocked() {
        World world = new World(10, 10);
        Chest chest = new Chest(Direction.RIGHT);
        world.restoreBuilding(1, 1, chest);
        Chest receiver = new Chest(Direction.UP); // faces off toward an empty cell — just absorbs
        world.restoreBuilding(2, 1, receiver);

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, VanillaItems.IRON_ORE));
        }

        world.tick();

        assertEquals(CAPACITY - 1, chest.count(), "one item must have moved on");
        assertEquals(BuildingStatus.WORKING, chest.appearance().status(),
                "it delivered this tick and is no longer at capacity — that is not a blocked chest");
    }

    @Test
    void chestBelowCapacityReportsWorkingStatus() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 1, chest);
        chest.accept(world, VanillaItems.GEAR);

        world.tick();

        assertEquals(BuildingStatus.WORKING, chest.appearance().status());
    }

    /** A vanilla technology's price, read from the registry the game itself researches through. */
    private static int costOf(com.rustorio.api.content.ContentId tech) {
        return VanillaTechs.frozen().get(tech).cost();
    }
}
