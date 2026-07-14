package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты хранения мира: соседи, правила постановки зданий, обход.
 *
 * <p><b>Важно про этот файл.</b> Он написан ТОЛЬКО через координаты
 * ({@code tile(x, y)}, {@code neighborBuilding(x, y, dir)}) и ничего не знает о том,
 * как мир хранит клетки. Это не стилистика, а условие: в задаче B1 мир изнутри станет
 * набором чанков, и тогда эти тесты обязаны пройти БЕЗ ЕДИНОЙ ПРАВКИ. Если после B1
 * их пришлось чинить — значит, устройство мира протекло наружу.
 */
class WorldTest {

    @Test
    @DisplayName("Соседа за краем поля не существует")
    void neighborRespectsEdges() {
        World world = World.generate(3, 2);
        world.place(1, 0, Building.create(Tool.BELT, Direction.EAST));

        assertNull(world.neighborBuilding(0, 0, Direction.WEST), "за левым краем никого нет");
        assertNull(world.neighborBuilding(0, 0, Direction.NORTH), "за верхним краем никого нет");
        assertInstanceOf(Belt.class, world.neighborBuilding(0, 0, Direction.EAST),
                "сосед справа — лента, которую мы поставили");
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
        assertSame(Direction.SOUTH, belt.dir());
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

    @Test
    @DisplayName("Мир сообщает буру про руду при постройке — здание не знает своих координат")
    void minerLearnsAboutOreWhenPlaced() {
        World world = World.generate(20, 20);
        world.place(6, 5, Building.create(Tool.MINER, Direction.EAST));   // на руде
        world.place(0, 0, Building.create(Tool.MINER, Direction.EAST));   // без руды

        assertTrue(((Miner) world.tile(6, 5).building()).onOre());
        assertFalse(((Miner) world.tile(0, 0).building()).onOre());
    }

    // ── Чанки (задача B1). Тесты ВЫШЕ не менялись — в этом и была цель. ──

    @Test
    @DisplayName("Пустой мир не создаёт ни одного куска")
    void emptyWorldAllocatesNothing() {
        World world = World.generate(250, 250);
        assertSame(0, world.chunkCount(), "пока в мир не заглянули — он ничего не занимает");

        world.tile(0, 0);
        assertSame(1, world.chunkCount(), "заглянули в один угол — создался один кусок");

        world.tile(200, 200);
        assertSame(2, world.chunkCount(), "далёкая клетка — ещё один кусок, а не весь мир");
    }

    @Test
    @DisplayName("Клетка далеко от начала координат работает как любая другая")
    void farAwayCellBehavesNormally() {
        World world = World.generate(250, 250);
        world.place(200, 200, Building.create(Tool.CHEST, Direction.EAST));

        assertInstanceOf(Chest.class, world.tile(200, 200).building());
        assertNull(world.tile(199, 200).building(), "соседняя клетка пуста");
    }

    @Test
    @DisplayName("Обход мира видит все здания и только их")
    void forEachBuildingVisitsEveryBuilding() {
        World world = World.generate(5, 5);
        world.place(1, 1, Building.create(Tool.CHEST, Direction.EAST));
        world.place(3, 2, Building.create(Tool.CHEST, Direction.EAST));

        int[] count = {0};
        world.forEachBuilding((x, y, building) -> count[0]++);
        assertSame(2, count[0]);
    }
}
