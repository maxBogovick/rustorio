package com.rustorio.domain.building;

import com.rustorio.domain.Item;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void fastLabHalvesResearchTimeStartingFromTheFirstBatch() {
        World world = new World(4, 4);
        // Открываем FAST_LAB ДО того, как лаборатория впервые подстроится под срок — именно этот
        // момент раньше терял тех-эффект (см. javadoc Lab про ProcessTimer, P2-05).
        world.addResearchPoints(Tech.FAST_LAB.cost());
        Lab lab = new Lab();

        assertTrue(lab.accept(world, Item.GEAR));

        int halvedTime = Math.max(1, RESEARCH_TIME / 2);
        for (int i = 0; i < halvedTime - 1; i++) {
            lab.tick(world, 0, 0);
            assertEquals(Tech.FAST_LAB.cost(), world.research().points());
        }
        lab.tick(world, 0, 0);
        assertEquals(Tech.FAST_LAB.cost() + 1, world.research().points(),
                "первая же порция обязана учитывать уже открытую технологию");
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

    @Test
    void labRejectsRawMaterials() {
        World world = new World(4, 4);
        Lab lab = new Lab();

        assertFalse(lab.accept(world, Item.IRON_ORE));
        assertFalse(lab.accept(world, Item.IRON_PLATE));
        assertFalse(lab.accept(world, Item.BRONZE_ORE));
        assertFalse(lab.accept(world, Item.ALLOY_PLATE));
    }
}
