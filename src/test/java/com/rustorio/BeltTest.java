package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BeltSegment}: сборка прямой цепочки лент в один сегмент, движение груза по нему на
 * одну клетку за мировой тик, и самое рискованное место рефакторинга — склейка/разрез сегментов
 * при постройке/сносе клетки.
 */
class BeltTest {

    @Test
    void chainOfBeltsMovesItemExactlyOneTileAtATimeToTheChest() {
        World world = new World(10, 10);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(1, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));
        Chest chest = new Chest();
        world.restore(3, 0, chest);

        Belt tail = (Belt) world.peek(0, 0);
        assertTrue(tail.accept(world, Item.IRON_ORE));

        world.tick();
        assertNull(((Belt) world.peek(0, 0)).heldItem());
        assertEquals(Item.IRON_ORE, ((Belt) world.peek(1, 0)).heldItem());
        assertEquals(0, chest.count(), "за один тик груз не должен доехать дальше одной клетки");

        world.tick();
        assertEquals(Item.IRON_ORE, ((Belt) world.peek(2, 0)).heldItem());
        assertEquals(0, chest.count());

        world.tick();
        assertNull(((Belt) world.peek(2, 0)).heldItem());
        assertEquals(1, chest.count());
    }

    @Test
    void placingATileBetweenTwoSegmentsMergesThemIntoOne() {
        World world = new World(10, 10);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));
        Belt first = (Belt) world.peek(0, 0);
        Belt third = (Belt) world.peek(2, 0);
        assertNotSame(first.segment(), third.segment(), "до моста — два разных сегмента");

        assertTrue(world.placeBelt(1, 0, Direction.RIGHT));

        assertSame(first.segment(), third.segment(), "после моста — один и тот же сегмент");
        assertEquals(3, first.segment().size());
    }

    @Test
    void removingTheMiddleTileSplitsTheSegmentAndTakesItsCargoWithIt() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeBelt(1, 0, Direction.RIGHT);
        world.placeBelt(2, 0, Direction.RIGHT);
        Belt tail = (Belt) world.peek(0, 0);
        Belt head = (Belt) world.peek(2, 0);
        assertEquals(3, tail.segment().size());

        tail.accept(world, Item.IRON_ORE);
        world.tick(); // едет tail -> mid

        Building removed = world.removeBuilding(1, 0);
        assertEquals(Item.IRON_ORE, removed.heldItem(), "груз снесённого тайла уходит вместе с ним");

        assertEquals(1, tail.segment().size(), "хвост остался один в своём куске");
        assertEquals(1, head.segment().size(), "голова стала отдельным куском в одну клетку");
        assertNotSame(tail.segment(), head.segment());
    }

    @Test
    void removingTheTailShrinksTheSegmentWithoutCreatingASplit() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeBelt(1, 0, Direction.RIGHT);
        Belt head = (Belt) world.peek(1, 0);

        world.removeBuilding(0, 0);

        assertEquals(1, head.segment().size());
        assertTrue(head.segment().isTail(head));
    }

    @Test
    void upgradingATileWithSpeedModuleKeepsItRecognizableAsASegmentNeighbor() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        Belt original = (Belt) world.peek(0, 0);

        // Апгрейд кладёт SpeedModule поверх ленты (см. UpgradeSpeedAction) — без Building.unwrap
        // в World.restore лента бы «потерялась» для соседей: их проверка instanceof Belt увидела
        // бы SpeedModule и не признала бы тайл частью цепочки.
        world.restore(0, 0, new SpeedModule(world.removeBuilding(0, 0)));
        world.placeBelt(1, 0, Direction.RIGHT);

        Belt neighbor = (Belt) world.peek(1, 0);
        assertSame(original.segment(), neighbor.segment());
        assertEquals(2, original.segment().size());
    }
}
