package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Туннель на границе {@link BeltSegment}: вход берёт от обычного сегмента, выход отдаёт в
 * обычный сегмент дальше.
 */
class UndergroundBeltTest {

    @Test
    void beltFeedsTunnelWhichExitsToBeltOnTheOtherSide() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeUndergroundIn(1, 0, Direction.RIGHT);
        // (2,0) и (3,0) пусты — туннель ныряет под ними, в пределах MAX_RANGE = 4.
        world.placeUndergroundOut(4, 0, Direction.RIGHT);
        world.placeBelt(5, 0, Direction.RIGHT);
        Chest chest = new Chest();
        world.restoreBuilding(6, 0, chest);

        Belt feed = (Belt) world.peek(0, 0).orElseThrow();
        assertTrue(feed.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 10; i++) {
            world.tick();
        }
        assertEquals(1, chest.count());
    }

    @Test
    void tunnelWithoutPartnerWithinRangeNeverDelivers() {
        World world = new World(10, 10);
        world.placeUndergroundIn(0, 0, Direction.RIGHT);
        // Пара дальше MAX_RANGE (4) — с учётом самой клетки входа это x=6, шаг 6.
        world.placeUndergroundOut(6, 0, Direction.RIGHT);

        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0).orElseThrow();
        assertTrue(in.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 5; i++) {
            world.tick();
        }
        assertEquals(Optional.of(Item.IRON_ORE), in.heldItem(), "пара вне дальности — груз остаётся ждать на входе");
    }

    @Test
    void tunnelFindsPartnerEvenWhenOutIsUpgradedWithSpeedModule() {
        World world = new World(10, 10);
        world.placeUndergroundIn(0, 0, Direction.RIGHT);
        world.placeUndergroundOut(3, 0, Direction.RIGHT);
        // Апгрейд выхода (клавиша U) кладёт SpeedModule поверх — без Building.unwrap в
        // findPartner вход решил бы, что пары больше нет вовсе, и туннель сломался бы НАВСЕГДА.
        world.restoreBuilding(3, 0, new SpeedModule(world.removeBuilding(3, 0).orElseThrow()));
        world.placeBelt(4, 0, Direction.RIGHT);
        Chest chest = new Chest();
        world.restoreBuilding(5, 0, chest);

        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0).orElseThrow();
        assertTrue(in.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 8; i++) {
            world.tick();
        }
        assertEquals(1, chest.count(), "апгрейженный выход туннеля всё ещё находится и работает");
    }

    /**
     * Found during a post-Phase-4 architecture review, not part of any original P-numbered task:
     * a side effect of P2-06 (making {@code prefersDescendingTick} depend only on {@code
     * direction}, not {@code kind}). A LEFT-facing entrance now ticks in the ASCENDING pass, so a
     * RIGHT-facing feeder belt (DESCENDING pass) that delivers straight into it does so in an
     * EARLIER phase of the same frame — the entrance's own tick, later that same frame, used to
     * see cargo that "just arrived" and relay it to the exit immediately, skipping the one-tick
     * settle that {@link Belt#arrivedThisTick} already enforces for plain belts. Fixed the same
     * way: {@code UndergroundBelt} now tracks its own arrival mark.
     */
    @Test
    void tunnelDoesNotRelayCargoReceivedFromACrossPhaseFeederInTheSameTick() {
        World world = new World(10, 10);
        world.placeBelt(5, 0, Direction.RIGHT); // descending pass — exits into (6,0)
        world.placeUndergroundIn(6, 0, Direction.LEFT); // ascending pass — searches toward x=2
        world.placeUndergroundOut(2, 0, Direction.LEFT); // step 4 — within MAX_RANGE
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest); // OUT (LEFT) exits into (1,0)

        Belt feed = (Belt) world.peek(5, 0).orElseThrow();
        assertTrue(feed.accept(world, Item.IRON_ORE));

        world.tick(); // feeder (phase 1) delivers into IN; IN's own tick (phase 2) follows same frame
        world.tick(); // IN relays to OUT here at the earliest — not before

        assertEquals(0, chest.count(),
                "cargo fed cross-phase into a tunnel entrance must not reach the exit's neighbor in 2 ticks");
    }

    @Test
    void tunnelTakesTheSameNumberOfTicksInEveryDirection() {
        assertEquals(ticksToDeliverThroughTunnel(Direction.RIGHT), ticksToDeliverThroughTunnel(Direction.LEFT));
    }

    /**
     * Builds belt → tunnel-in → tunnel-out → belt → chest, all facing {@code direction}, feeds one
     * item at the start, and counts ticks until it lands in the chest — see P2-06.
     */
    private static int ticksToDeliverThroughTunnel(Direction direction) {
        World world = new World(10, 10);
        int dx = direction.dx();
        int startX = direction == Direction.RIGHT ? 0 : 9;

        int beltX = startX;
        int inX = startX + dx;
        int outX = startX + dx * 4; // in + MAX_RANGE
        int outBeltX = startX + dx * 5;
        int chestX = startX + dx * 6;

        world.placeBelt(beltX, 0, direction);
        world.placeUndergroundIn(inX, 0, direction);
        world.placeUndergroundOut(outX, 0, direction);
        world.placeBelt(outBeltX, 0, direction);
        Chest chest = new Chest();
        world.restoreBuilding(chestX, 0, chest);

        Belt feed = (Belt) world.peek(beltX, 0).orElseThrow();
        assertTrue(feed.accept(world, Item.IRON_ORE));

        int ticks = 0;
        while (chest.count() == 0) {
            world.tick();
            ticks++;
            if (ticks > 20) {
                throw new IllegalStateException("cargo never arrived within a sane number of ticks");
            }
        }
        return ticks;
    }
}
