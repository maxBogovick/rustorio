package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты одной транспортной линии (L6): вместимость, движение без обгона, плавная
 * интерполяция для рендера.
 *
 * <p>Проверяем через {@link World} (сегменты собирает {@link BeltNetwork}), но
 * смотрим на саму линию — склейку и разрез двух РАЗНЫХ линий проверит L7.
 */
class BeltSegmentTest {

    /** Прямая лента длиной {@code length} клеток, идущая на восток из (0,0). */
    private static World beltLine(int length) {
        World world = World.generate(length + 2, 1);
        for (int x = 0; x < length; x++) {
            world.place(x, 0, Building.create(Tool.BELT, Direction.EAST));
        }
        return world;
    }

    private static BeltSegment segment(World world, int x) {
        Belt belt = (Belt) world.tile(x, 0).building();
        BeltSegment segment = belt.segment();
        assertTrue(segment != null, "лента должна быть привязана к линии");
        segment.assertInvariants();
        return segment;
    }

    @Test
    @DisplayName("Клетка вмещает SLOTS_PER_TILE предметов, а не один")
    void tileHoldsMoreThanOneItem() {
        World world = beltLine(3);
        BeltSegment segment = segment(world, 0);
        assertEquals(3 * Config.SLOTS_PER_TILE, segment.lengthSlots());

        segment.insert(Item.IRON_ORE, 0);
        segment.insert(Item.IRON_ORE, 1);
        segment.assertInvariants();

        assertEquals(2, segment.countItemsOnTile(0), "на одной клетке едут два предмета");
        assertFalse(segment.canInsertAt(0), "занятый слот больше не принимает");
    }

    @Test
    @DisplayName("Предмет доезжает до головы и останавливается")
    void itemStopsAtHead() {
        World world = beltLine(3);
        BeltSegment segment = segment(world, 0);
        segment.insert(Item.IRON_ORE, 0);

        for (int i = 0; i < 10; i++) {
            segment.step(Config.BELT_SLOTS_PER_TICK);
            segment.assertInvariants();
        }

        assertEquals(1, segment.itemCount(), "предмет не должен исчезнуть на краю");
        assertEquals(Item.IRON_ORE, segment.headItem().orElse(null),
                "предмет должен стоять в последнем слоте и ждать приёмника");
    }

    @Test
    @DisplayName("Предметы не обгоняют друг друга и не сливаются")
    void itemsDoNotOvertake() {
        World world = beltLine(4);
        BeltSegment segment = segment(world, 0);
        segment.insert(Item.IRON_ORE, 0);
        segment.insert(Item.IRON_PLATE, 1);

        for (int i = 0; i < 20; i++) {
            segment.step(Config.BELT_SLOTS_PER_TICK);
            segment.assertInvariants(); // инвариант сам ловит «два предмета в одном слоте»
        }

        assertEquals(2, segment.itemCount());
        // Порядок сохранён: пластина шла впереди — она и стоит у головы.
        assertEquals(Item.IRON_PLATE, segment.headItem().orElse(null));
        List<BeltItemPos> positions = segment.itemPositions(1f);
        assertTrue(positions.get(0).x() > positions.get(1).x(),
                "передний предмет обязан оставаться впереди");
    }

    @Test
    @DisplayName("Между тиками предмет рисуется МЕЖДУ старой и новой позицией")
    void renderInterpolatesBetweenTicks() {
        World world = beltLine(4);
        BeltSegment segment = segment(world, 0);
        segment.insert(Item.IRON_ORE, 0);

        float before = segment.itemPositions(0f).getFirst().x();
        segment.step(Config.BELT_SLOTS_PER_TICK);

        float atStart = segment.itemPositions(0f).getFirst().x();
        float atHalf = segment.itemPositions(0.5f).getFirst().x();
        float atEnd = segment.itemPositions(1f).getFirst().x();

        assertEquals(before, atStart, 1e-4f, "в начале тика предмет ещё на старом месте");
        assertTrue(atHalf > atStart && atHalf < atEnd,
                "в середине тика — между старой и новой позицией: это и есть плавность");
    }
}
