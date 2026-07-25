package com.rustorio.domain.building;

import com.rustorio.domain.Item;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
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
        world.addResearchPoints(Tech.FAST_LAB.cost());
        Lab lab = new Lab();

        // Первая порция варится ПОЛНЫЙ RESEARCH_TIME, даже если технология уже открыта: таймер
        // лаборатории создаётся полем при постройке, а не лениво при первом accept, как у Furnace.
        assertTrue(lab.accept(world, Item.GEAR));
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
        }
        lab.tick(world, 0, 0);
        assertEquals(Tech.FAST_LAB.cost() + 1, world.research().points());

        // Вторая порция — таймер уже пересобран effectiveTime(world) в момент готовности первой.
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
