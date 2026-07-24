package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Лаборатория + {@link ProcessTimer}: очко исследований в срок и тех-модификация FAST_LAB. */
class LabTest {

    private static final int RESEARCH_TIME = 10; // зеркалит Lab.RESEARCH_TIME (приватная константа)

    @Test
    void convertsGearIntoResearchPointAfterResearchTime() {
        World world = new World(4, 4);
        Lab lab = new Lab();

        assertTrue(lab.accept(world, Item.GEAR));
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
            assertEquals(0, world.research().points(), "не должно быть готово раньше срока");
        }
        lab.tick(world, 0, 0);
        assertEquals(1, world.research().points());
    }

    @Test
    void fastLabHalvesResearchTimeStartingFromTheSecondBatch() {
        World world = new World(4, 4);
        world.research().addPoints(Tech.FAST_LAB.cost());
        Lab lab = new Lab();

        // Первая порция варится ПОЛНЫЙ RESEARCH_TIME, даже если технология уже открыта: таймер
        // лаборатории создаётся полем при постройке (Lab() не получает World), а не лениво при
        // первом accept, как у Furnace, — значит, эффективное время впервые читается только при
        // СБРОСЕ после готовности, не раньше. Тот же класс ситуаций, что чинили в Furnace.accept,
        // здесь оставлен как есть: зацепки вида "recipe == null" у Lab нет, а польза от отдельного
        // редизайна конструктора ради одного тика на всю игру не стоит риска.
        assertTrue(lab.accept(world, Item.GEAR));
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
        }
        lab.tick(world, 0, 0);
        // +1 к уже накопленным для разблокировки FAST_LAB очкам (addPoints суммирует, не сбрасывает).
        assertEquals(Tech.FAST_LAB.cost() + 1, world.research().points());

        // Вторая порция — таймер уже пересобран effectiveTime(world) в момент готовности первой,
        // и вот тут FAST_LAB реально ускоряет цикл вдвое.
        assertTrue(lab.accept(world, Item.MECHANISM));
        int halvedTime = Math.max(1, RESEARCH_TIME / 2);
        for (int i = 0; i < halvedTime - 1; i++) {
            lab.tick(world, 0, 0);
        }
        lab.tick(world, 0, 0);
        assertEquals(Tech.FAST_LAB.cost() + 2, world.research().points());
    }

    @Test
    void acceptsChassisLikeAnyOtherTopLevelGoods() {
        World world = new World(4, 4);
        Lab lab = new Lab();

        assertTrue(lab.accept(world, Item.CHASSIS));
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
        }
        lab.tick(world, 0, 0);
        assertEquals(1, world.research().points(), "шасси стоит столько же очков, сколько шестерёнка");
    }

    @Test
    void acceptsAlloyGearLikeAnyOtherTopLevelGoods() {
        World world = new World(4, 4);
        Lab lab = new Lab();

        assertTrue(lab.accept(world, Item.ALLOY_GEAR));
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
        }
        lab.tick(world, 0, 0);
        assertEquals(1, world.research().points());
    }
}
