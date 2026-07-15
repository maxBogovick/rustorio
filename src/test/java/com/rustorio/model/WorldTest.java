package com.rustorio.model;

import com.rustorio.core.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Мир — чистая модель без движка: тестируется без окна и OpenGL.
 * Эти тесты стерегут два свойства фундамента, на которых стоит весь курс:
 * ленивые чанки и детерминированную руду.
 */
class WorldTest {

    @Test
    void freshWorldCreatesNoChunks() {
        // Кусок мира создаётся, только когда в него заглянули. Пустой мир —
        // ноль кусков: память не тратится на клетки, которых никто не видел.
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        assertEquals(0, world.chunkCount());
    }

    @Test
    void readingOneTileCreatesExactlyOneChunk() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        world.tile(0, 0);
        assertEquals(1, world.chunkCount());
        // Соседняя клетка того же куска 32×32 второй кусок не создаёт.
        world.tile(5, 7);
        assertEquals(1, world.chunkCount());
    }

    @Test
    void oreIsDeterministic() {
        // Руда — функция от координат: два независимых мира обязаны согласиться
        // о каждой клетке. Без этого не будет ни сохранений, ни честных тестов.
        World a = World.generate(Config.GRID_W, Config.GRID_H);
        World b = World.generate(Config.GRID_W, Config.GRID_H);
        for (int y = 0; y < Config.GRID_H; y += 3) {
            for (int x = 0; x < Config.GRID_W; x += 3) {
                assertEquals(a.tile(x, y).hasOre(), b.tile(x, y).hasOre(),
                        "клетка (" + x + ", " + y + ")");
            }
        }
        // И залежи вообще существуют: центр первой — (6, 5).
        assertTrue(a.tile(6, 5).hasOre());
        assertFalse(a.tile(20, 30).hasOre());
    }

    @Test
    void inBoundsMatchesWorldSize() {
        World world = World.generate(10, 8);
        assertTrue(world.inBounds(0, 0));
        assertTrue(world.inBounds(9, 7));
        assertFalse(world.inBounds(10, 0));
        assertFalse(world.inBounds(0, 8));
        assertFalse(world.inBounds(-1, 0));
    }
}
