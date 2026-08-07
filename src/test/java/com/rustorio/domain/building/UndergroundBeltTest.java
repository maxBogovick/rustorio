package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.UpgradeSpeedAction;
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

    /**
     * (N13, NEW_BUGS_PROGRESS.md — owner decision) A speed upgrade on a tunnel half is refused, the
     * same way P2-03 already refuses one on a {@link Belt}. Not because it breaks anything — the
     * external review claimed it desynchronizes the tick phases, and that turned out to be wrong: a
     * doubled {@code tick} call would find {@code held} already empty and return immediately, so
     * nothing moves twice in a tick. It's refused because it therefore does NOTHING while still
     * charging the player for the upgrade, and "sold a module that can't possibly help" is the
     * exact honesty argument P2-03 made for belts.
     */
    @Test
    void upgradingATunnelHalfIsRefused() {
        World world = new World(10, 10);
        world.placeUndergroundIn(1, 0, Direction.RIGHT);
        world.placeUndergroundOut(3, 0, Direction.RIGHT);

        ActionHistory history = new ActionHistory();
        history.perform(world, new UpgradeSpeedAction(1, 0));
        history.perform(world, new UpgradeSpeedAction(3, 0));

        assertEquals(0, world.peek(1, 0).orElseThrow().speedLevel(), "an entrance must not take the module");
        assertEquals(0, world.peek(3, 0).orElseThrow().speedLevel(), "and neither must an exit");
        assertEquals(VanillaBuildings.idFor(BuildingType.UNDERGROUND_IN), world.peek(1, 0).orElseThrow().prototypeId(),
                "and the refusal must leave the tunnel itself on the map, untouched");
    }

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
        assertTrue(feed.accept(world, VanillaItems.IRON_ORE));

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
        assertTrue(in.accept(world, VanillaItems.IRON_ORE));

        for (int i = 0; i < 5; i++) {
            world.tick();
        }
        assertEquals(Optional.of(VanillaItems.IRON_ORE), in.heldItem(), "пара вне дальности — груз остаётся ждать на входе");
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
        assertTrue(feed.accept(world, VanillaItems.IRON_ORE));

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
        assertTrue(feed.accept(world, VanillaItems.IRON_ORE));

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
