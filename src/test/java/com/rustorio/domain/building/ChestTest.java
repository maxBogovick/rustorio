package com.rustorio.domain.building;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
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

        assertEquals(Sprite.CHEST, chest.appearance().sprite());
        assertFalse(chest.appearance().hasBadge(), "an empty chest must not draw a \"0\" badge");
    }

    @Test
    void filledChestShowsItsCount() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        chest.accept(world, Item.IRON_ORE);
        chest.accept(world, Item.IRON_ORE);

        assertTrue(chest.appearance().hasBadge());
        assertEquals(2, chest.appearance().badge());
    }

    @Test
    void chestAcceptsAnyItem() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        for (Item item : Item.values()) {
            assertTrue(chest.accept(world, item), "a chest must accept every item kind, unsorted");
        }
        assertEquals(Item.values().length, chest.count());
    }

    /** (D-02, DEV_TASKS.md) §2.5's actual defect: item TYPE used to be thrown away on accept. */
    @Test
    void tracksEachItemKindSeparately() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        chest.accept(world, Item.IRON_ORE);
        chest.accept(world, Item.IRON_ORE);
        chest.accept(world, Item.BRONZE_ORE);

        assertEquals(2, chest.amount(Item.IRON_ORE));
        assertEquals(1, chest.amount(Item.BRONZE_ORE));
        assertEquals(0, chest.amount(Item.GEAR), "never offered any GEAR — must read back as zero, not stay unset oddly");
        assertEquals(3, chest.count(), "the badge total is still the sum across every kind");
    }

    private static final int CAPACITY = 100; // mirrors Chest.CAPACITY (private constant)

    @Test
    void refusesNewItemsOnceAtCapacity() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, Item.IRON_ORE), "unit " + i + " should still fit under the cap");
        }

        assertFalse(chest.accept(world, Item.IRON_ORE), "the chest is full — one more must be refused");
        assertFalse(chest.accept(world, Item.BRONZE_ORE), "full is full regardless of which kind is offered next");
        assertEquals(CAPACITY, chest.count());
    }

    /** (D-02, DEV_TASKS.md) §2.5: "тех BIG_BUFFER к ящику не применяется, хотя по названию должен." */
    @Test
    void bigBufferDoublesTheCapacity() {
        World world = new World(4, 4);
        world.addResearchPoints(Tech.FAST_MINING.cost());
        assertTrue(world.tryUnlockTech(Tech.FAST_MINING));
        world.addResearchPoints(Tech.FAST_SMELTING.cost());
        assertTrue(world.tryUnlockTech(Tech.FAST_SMELTING));
        world.addResearchPoints(Tech.BIG_BUFFER.cost());
        assertTrue(world.tryUnlockTech(Tech.BIG_BUFFER));
        Chest chest = new Chest();

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(chest.accept(world, Item.IRON_ORE));
        }
        assertTrue(chest.accept(world, Item.IRON_ORE), "BIG_BUFFER must have doubled the cap past the un-teched " + CAPACITY);
        assertEquals(CAPACITY + 1, chest.count());
    }

    /** (D-02, DEV_TASKS.md) The other half of §2.5's fix: a chest is a buffer now, not a sink — it must give items back out. */
    @Test
    void pushesOneStoredItemPerTickOutThroughItsDirection() {
        World world = new World(4, 4);
        world.restoreBuilding(1, 1, new Chest(Direction.RIGHT));
        Chest sink = new Chest(Direction.LEFT); // faces off the map — a passive receiver for this assertion
        world.restoreBuilding(2, 1, sink);
        Chest source = (Chest) world.peek(1, 1).orElseThrow();
        source.accept(world, Item.GEAR);

        world.tick();

        assertEquals(0, source.count(), "the item must have left the source chest");
        assertEquals(1, sink.amount(Item.GEAR), "…and arrived at the neighbor in its output direction");
    }

    @Test
    void rotatingChangesDirectionButKeepsContents() {
        World world = new World(4, 4);
        Chest chest = new Chest(Direction.RIGHT);
        chest.accept(world, Item.GEAR);

        Chest rotated = (Chest) chest.rotatedClockwise().orElseThrow();

        assertEquals(Optional.of(Direction.DOWN), rotated.outputDirection());
        assertEquals(1, rotated.amount(Item.GEAR), "rotating must not drop what was already stored");
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
            assertTrue(chest.accept(world, Item.IRON_ORE));
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
            assertTrue(chest.accept(world, Item.IRON_ORE));
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
            assertTrue(chest.accept(world, Item.IRON_ORE));
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
        chest.accept(world, Item.GEAR);

        world.tick();

        assertEquals(BuildingStatus.WORKING, chest.appearance().status());
    }
}
