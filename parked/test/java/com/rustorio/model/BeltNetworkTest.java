package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Тесты сборки и разборки линий — самое тонкое место игры.
 *
 * <p>Здесь предметы теряются и удваиваются: при разрезе ошибка на единицу в
 * границе отправляет предмет либо в обе половины, либо ни в одну. Поэтому в конце
 * стоит property-тест: тысяча случайных построек и сносов с проверкой того, что
 * предметы не рождаются и не пропадают сами по себе.
 */
class BeltNetworkTest {

    private static void placeBelt(World world, int x, Direction dir) {
        world.place(x, 0, Building.create(Tool.BELT, dir));
    }

    private static BeltSegment segmentAt(World world, int x) {
        Belt belt = (Belt) world.tile(x, 0).building();
        assertNotNull(belt, "на (" + x + ",0) должна быть лента");
        BeltSegment segment = belt.segment();
        assertNotNull(segment, "лента должна быть привязана к линии");
        return segment;
    }

    private static void checkAll(World world) {
        for (BeltSegment segment : world.belts().segments()) {
            segment.assertInvariants();
        }
    }

    @Test
    @DisplayName("Пять лент в ряд — это ОДНА линия, а не пять")
    void beltsInARowFormOneSegment() {
        World world = World.generate(8, 1);
        for (int x = 0; x < 5; x++) {
            placeBelt(world, x, Direction.EAST);
        }
        checkAll(world);

        assertEquals(1, world.belts().segments().size());
        BeltSegment segment = segmentAt(world, 0);
        assertEquals(5, segment.tileCount());
        assertEquals(5 * Config.SLOTS_PER_TILE, segment.lengthSlots());
        // Все пять клеток смотрят на ОДИН и тот же объект линии.
        for (int x = 1; x < 5; x++) {
            assertSame(segment, segmentAt(world, x));
        }
    }

    @Test
    @DisplayName("Лента, построенная МЕЖДУ двумя линиями, склеивает их — предметы целы")
    void placingBetweenTwoSegmentsMergesThem() {
        World world = World.generate(8, 1);
        placeBelt(world, 0, Direction.EAST);
        placeBelt(world, 1, Direction.EAST);   // линия A: клетки 0,1
        placeBelt(world, 3, Direction.EAST);
        placeBelt(world, 4, Direction.EAST);   // линия B: клетки 3,4
        assertEquals(2, world.belts().segments().size());

        segmentAt(world, 0).insert(Item.IRON_ORE, 0);     // предмет в хвосте A
        segmentAt(world, 3).insert(Item.IRON_PLATE, 0);   // предмет в хвосте B
        assertEquals(2, world.belts().itemCount());

        placeBelt(world, 2, Direction.EAST); // ← склейка
        checkAll(world);

        assertEquals(1, world.belts().segments().size(), "должна остаться одна линия");
        BeltSegment merged = segmentAt(world, 0);
        assertEquals(5, merged.tileCount());
        assertEquals(2, world.belts().itemCount(), "предметы не потерялись и не удвоились");

        // Ключевая проверка склейки: предмет линии B ДОЛЖЕН был сдвинуться и
        // остаться на своей клетке (3), а не «телепортироваться» в начало.
        assertEquals(1, merged.countItemsOnTile(0), "предмет линии A — на клетке 0");
        assertEquals(1, merged.countItemsOnTile(3), "предмет линии B — по-прежнему на клетке 3");
    }

    @Test
    @DisplayName("Снос середины разрезает линию надвое; предмет на снесённой клетке уничтожен")
    void removingMiddleSplitsSegment() {
        World world = World.generate(8, 1);
        for (int x = 0; x < 5; x++) {
            placeBelt(world, x, Direction.EAST);
        }
        BeltSegment segment = segmentAt(world, 0);
        segment.insert(Item.IRON_ORE, 0);                          // клетка 0
        segment.insert(Item.IRON_PLATE, 2 * Config.SLOTS_PER_TILE); // клетка 2 — её снесут
        segment.insert(Item.GEAR, 4 * Config.SLOTS_PER_TILE);      // клетка 4
        assertEquals(3, world.belts().itemCount());

        world.remove(2, 0); // ← разрез
        checkAll(world);

        assertEquals(2, world.belts().segments().size(), "должно стать две линии");
        assertEquals(2, world.belts().itemCount(),
                "уничтожен ровно один предмет — тот, что стоял на снесённой клетке");

        BeltSegment left = segmentAt(world, 0);
        BeltSegment right = segmentAt(world, 3);
        assertEquals(2, left.tileCount());
        assertEquals(2, right.tileCount());
        assertEquals(1, left.countItemsOnTile(0), "предмет левой половины на месте");
        // Клетка 4 стала в правой линии клеткой с индексом 1 — позиция пересчитана.
        assertEquals(1, right.countItemsOnTile(1), "предмет правой половины пересчитан верно");
    }

    @Test
    @DisplayName("Никакая последовательность построек и сносов не создаёт и не теряет предметы")
    void itemsAreConservedUnderRandomEdits() {
        // Зерно фиксировано: упавший тест должен воспроизводиться.
        Random random = new Random(42);
        int width = 10;
        World world = World.generate(width, 1);
        int expected = 0;

        for (int op = 0; op < 2000; op++) {
            int x = random.nextInt(width);
            switch (random.nextInt(4)) {
                case 0 -> // построить ленту (всегда EAST: тогда одинаковую не пересоздаём)
                        placeBelt(world, x, Direction.EAST);
                case 1 -> {
                    // снести ленту: законно уничтожаются предметы, стоявшие на ней
                    if (world.tile(x, 0).building() instanceof Belt belt) {
                        BeltSegment segment = belt.segment();
                        assertNotNull(segment);
                        expected -= segment.countItemsOnTile(belt.indexInSegment());
                        world.remove(x, 0);
                    }
                }
                case 2 -> {
                    // положить предмет на ленту
                    if (world.tile(x, 0).building() instanceof Belt belt
                            && belt.canAccept(Item.IRON_ORE)) {
                        belt.accept(Item.IRON_ORE);
                        expected++;
                    }
                }
                default -> world.belts().step(Config.BELT_SLOTS_PER_TICK);
            }

            checkAll(world);
            assertEquals(expected, world.belts().itemCount(),
                    "после операции №" + op + " число предметов разошлось с ожидаемым");
        }
    }
}
