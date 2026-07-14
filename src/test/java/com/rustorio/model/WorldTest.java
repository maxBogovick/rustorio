package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Тесты хранения мира: границы соседей и правила постановки зданий. */
class WorldTest {

    @Test
    @DisplayName("neighbor возвращает пусто на краю и корректный индекс внутри")
    void neighborRespectsEdges() {
        World world = World.generate(3, 2);
        assertTrue(world.neighbor(0, Direction.WEST).isEmpty());
        assertTrue(world.neighbor(0, Direction.NORTH).isEmpty());
        assertEquals(OptionalInt.of(1), world.neighbor(0, Direction.EAST));
        assertEquals(OptionalInt.of(3), world.neighbor(0, Direction.SOUTH)); // (0,1) = 1*3+0
    }

    @Test
    @DisplayName("Повторная постановка не сбрасывает содержимое, другой тип не затирает")
    void placeProtectsExistingBuilding() {
        World world = World.generate(1, 1);
        world.place(0, 0, Building.create(Tool.BELT, Direction.EAST));
        ((Belt) world.tile(0, 0).building()).accept(Item.IRON_ORE);

        // Такая же лента поверх — предмет на месте.
        world.place(0, 0, Building.create(Tool.BELT, Direction.EAST));
        Belt belt = assertInstanceOf(Belt.class, world.tile(0, 0).building());
        assertTrue(belt.item().isPresent(), "повторная постановка не должна сбрасывать предмет");

        // Другой тип поверх — существующее здание НЕ затёрто.
        world.place(0, 0, Building.create(Tool.MINER, Direction.EAST));
        assertInstanceOf(Belt.class, world.tile(0, 0).building());
    }

    @Test
    @DisplayName("Смена направления тем же типом обновляет здание")
    void placeReplacesWhenDirectionDiffers() {
        World world = World.generate(1, 1);
        world.place(0, 0, Building.create(Tool.BELT, Direction.EAST));
        world.place(0, 0, Building.create(Tool.BELT, Direction.SOUTH));
        Belt belt = assertInstanceOf(Belt.class, world.tile(0, 0).building());
        assertEquals(Direction.SOUTH, belt.dir());
    }

    @Test
    @DisplayName("Постановка вне поля безопасна (ничего не происходит)")
    void placeOutOfBoundsIsSafe() {
        World world = World.generate(2, 2);
        world.place(-1, 0, Building.create(Tool.BELT, Direction.EAST));
        world.place(5, 5, Building.create(Tool.BELT, Direction.EAST));
        assertFalse(world.inBounds(-1, 0));
        assertFalse(world.inBounds(5, 5));
    }
}
