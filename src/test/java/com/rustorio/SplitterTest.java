package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сплиттер на границе {@link BeltSegment}: он не участвует ни в одном сегменте (не {@code Belt}),
 * но должен без изменений принимать от сегмента и отдавать в сегмент по обе стороны — этот тест
 * проверяет именно ГРАНИЦУ после переделки {@link Belt#tick} в {@link BeltTest}.
 */
class SplitterTest {

    @Test
    void beltFeedsSplitterWhichRoutesByRule() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeSplitter(1, 0, Direction.RIGHT);
        world.placeBelt(2, 0, Direction.RIGHT);
        Chest chestForward = new Chest();
        world.restore(3, 0, chestForward);
        Chest chestDown = new Chest();
        world.restore(1, 1, chestDown);

        Belt feed = (Belt) world.peek(0, 0);
        assertTrue(feed.accept(world, Item.IRON_ORE)); // SortRule.ORE_FORWARD -> вперёд

        for (int i = 0; i < 6; i++) {
            world.tick();
        }
        assertEquals(1, chestForward.count(), "руда едет по сегменту, через сплиттер, дальше по сегменту");
        assertEquals(0, chestDown.count());
    }

    @Test
    void splitterRoutesNonOreDownByRule() {
        World world = new World(10, 10);
        world.placeSplitter(0, 0, Direction.RIGHT);
        Chest chestForward = new Chest();
        world.restore(1, 0, chestForward);
        Chest chestDown = new Chest();
        world.restore(0, 1, chestDown);

        Splitter splitter = (Splitter) world.peek(0, 0);
        assertTrue(splitter.accept(world, Item.IRON_PLATE)); // не руда -> вниз

        world.tick();
        assertEquals(0, chestForward.count());
        assertEquals(1, chestDown.count());
    }

    @Test
    void rotatingTheSplitterRotatesBothOutputsTogether() {
        // facing=DOWN: "вперёд" правила теперь смотрит вниз, а повёрнутый по часовой от DOWN —
        // это LEFT (см. Direction.rotate: RIGHT->DOWN->LEFT->UP->RIGHT) — не то же самое, что было
        // бы при facing=RIGHT (там вперёд/вниз были RIGHT/DOWN). Раньше R на сплиттер не влиял
        // вообще; этот тест как раз про то, что теперь влияет.
        World world = new World(10, 10);
        world.placeSplitter(1, 1, Direction.DOWN);
        Chest chestForward = new Chest();
        world.restore(1, 2, chestForward); // (x, y+1) — по направлению DOWN
        Chest chestRotated = new Chest();
        world.restore(0, 1, chestRotated); // (x-1, y) — по направлению LEFT

        Splitter splitter = (Splitter) world.peek(1, 1);
        assertTrue(splitter.accept(world, Item.IRON_ORE)); // руда -> "вперёд" -> DOWN

        world.tick();
        assertEquals(1, chestForward.count());
        assertEquals(0, chestRotated.count());

        assertTrue(splitter.accept(world, Item.IRON_PLATE)); // не руда -> "повёрнуто" -> LEFT
        world.tick();
        assertEquals(1, chestRotated.count());
    }

    @Test
    void saveAndLoadRoundTripPreservesFacingAndCargo() {
        Splitter original = new Splitter(SortRule.ORE_FORWARD, Direction.UP);
        original.accept(null, Item.GEAR);

        Splitter reloaded = Splitter.load(original.save());

        assertEquals(Direction.UP, reloaded.outputDirection());
        assertEquals(Item.GEAR, reloaded.heldItem());
    }

    @Test
    void saveAndLoadRoundTripPreservesEmptyCargo() {
        Splitter original = new Splitter(SortRule.ORE_FORWARD, Direction.LEFT);

        Splitter reloaded = Splitter.load(original.save());

        assertEquals(Direction.LEFT, reloaded.outputDirection());
        assertNull(reloaded.heldItem());
    }
}
