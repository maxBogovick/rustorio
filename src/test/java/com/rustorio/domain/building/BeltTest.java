package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BeltSegment}: assembling a straight belt run into one segment, moving cargo along it one
 * tile per world tick, and the riskiest spot of all — merging/splitting segments on build/demolish.
 */
class BeltTest {

    private static Belt beltAt(World world, int x, int y) {
        return (Belt) world.peek(x, y).orElseThrow();
    }

    @Test
    void chainOfBeltsMovesItemExactlyOneTileAtATimeToTheChest() {
        World world = new World(10, 10);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(1, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));
        Chest chest = new Chest();
        world.restoreBuilding(3, 0, chest);

        Belt tail = beltAt(world, 0, 0);
        assertTrue(tail.accept(world, Item.IRON_ORE));

        world.tick();
        assertEquals(Optional.empty(), beltAt(world, 0, 0).heldItem());
        assertEquals(Optional.of(Item.IRON_ORE), beltAt(world, 1, 0).heldItem());
        assertEquals(0, chest.count(), "за один тик груз не должен доехать дальше одной клетки");

        world.tick();
        assertEquals(Optional.of(Item.IRON_ORE), beltAt(world, 2, 0).heldItem());
        assertEquals(0, chest.count());

        world.tick();
        assertEquals(Optional.empty(), beltAt(world, 2, 0).heldItem());
        assertEquals(1, chest.count());
    }

    @Test
    void placingATileBetweenTwoSegmentsMergesThemIntoOne() {
        World world = new World(10, 10);
        assertTrue(world.placeBelt(0, 0, Direction.RIGHT));
        assertTrue(world.placeBelt(2, 0, Direction.RIGHT));
        Belt first = beltAt(world, 0, 0);
        Belt third = beltAt(world, 2, 0);
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
        Belt tail = beltAt(world, 0, 0);
        Belt head = beltAt(world, 2, 0);
        assertEquals(3, tail.segment().size());

        tail.accept(world, Item.IRON_ORE);
        world.tick(); // едет tail -> mid

        Building removed = world.removeBuilding(1, 0).orElseThrow();
        assertEquals(Optional.of(Item.IRON_ORE), removed.heldItem(), "груз снесённого тайла уходит вместе с ним");

        assertEquals(1, tail.segment().size(), "хвост остался один в своём куске");
        assertEquals(1, head.segment().size(), "голова стала отдельным куском в одну клетку");
        assertNotSame(tail.segment(), head.segment());
    }

    @Test
    void removingTheTailShrinksTheSegmentWithoutCreatingASplit() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeBelt(1, 0, Direction.RIGHT);
        Belt head = beltAt(world, 1, 0);

        world.removeBuilding(0, 0);

        assertEquals(1, head.segment().size());
        assertTrue(head.segment().isTail(head));
    }

    @Test
    void upgradingATileWithSpeedModuleKeepsItRecognizableAsASegmentNeighbor() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        Belt original = beltAt(world, 0, 0);

        // Апгрейд кладёт SpeedModule поверх ленты (см. UpgradeSpeedAction) — без Building.unwrap
        // в World.restoreBuilding лента бы «потерялась» для соседей: их проверка instanceof Belt
        // увидела бы SpeedModule и не признала бы тайл частью цепочки.
        world.restoreBuilding(0, 0, new SpeedModule(world.removeBuilding(0, 0).orElseThrow()));
        world.placeBelt(1, 0, Direction.RIGHT);

        Belt neighbor = beltAt(world, 1, 0);
        assertSame(original.segment(), neighbor.segment());
        assertEquals(2, original.segment().size());
    }
}
