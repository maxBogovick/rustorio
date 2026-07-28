package com.rustorio.domain.building;

import com.rustorio.domain.Item;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Лаборатория + {@link ProcessTimer}: очко исследований в срок, тех-модификация FAST_LAB, и
 * (P-01, DEV_TASKS.md) очки за партию пропорциональны глубине предмета в {@link RecipeBook}, а не
 * фиксированы. {@code GEAR}=13 тиков глубины — базовая единица (1 очко, как и раньше); {@code
 * ALLOY_GEAR}=30 → 2, {@code CHASSIS}=66 → 5 — числа сверены с примерами из §2.4 аудита.
 */
class LabTest {

    private static final int RESEARCH_TIME = 10; // зеркалит Lab.RESEARCH_TIME (приватная константа)
    private static final RecipeBook RECIPES = RecipeBook.standard();

    private static void tickUntilDone(Lab lab, World world) {
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
        }
        lab.tick(world, 0, 0);
    }

    @Test
    void convertsGearIntoResearchPointAfterResearchTime() {
        World world = new World(4, 4);
        Lab lab = new Lab(RECIPES);

        assertTrue(lab.accept(world, Item.GEAR));
        for (int i = 0; i < RESEARCH_TIME - 1; i++) {
            lab.tick(world, 0, 0);
            assertEquals(0, world.research().points(), "не должно быть готово раньше срока");
        }
        lab.tick(world, 0, 0);
        assertEquals(10, world.research().points(), "GEAR — база пересчёта: ровно POINTS_PER_GEAR (N15)");
    }

    @Test
    void fastLabHalvesResearchTimeStartingFromTheFirstBatch() {
        World world = new World(4, 4);
        // Открываем FAST_LAB ДО того, как лаборатория впервые подстроится под срок — именно этот
        // момент раньше терял тех-эффект (см. javadoc Lab про ProcessTimer, P2-05). Разблокировка
        // теперь явный выбор игрока (P-02, DEV_TASKS.md), и FAST_LAB требует и FAST_SMELTING, и
        // BIG_BUFFER, каждый из которых требует FAST_MINING — открываем всю цепочку по порядку.
        unlockWithPoints(world, Tech.FAST_MINING);
        unlockWithPoints(world, Tech.FAST_SMELTING);
        unlockWithPoints(world, Tech.BIG_BUFFER);
        unlockWithPoints(world, Tech.FAST_LAB);
        assertEquals(0, world.research().points(), "цепочка потратила ровно столько очков, сколько было начислено");

        Lab lab = new Lab(RECIPES);
        assertTrue(lab.accept(world, Item.GEAR));

        int halvedTime = Math.max(1, RESEARCH_TIME / 2);
        for (int i = 0; i < halvedTime - 1; i++) {
            lab.tick(world, 0, 0);
            assertEquals(0, world.research().points());
        }
        lab.tick(world, 0, 0);
        assertEquals(10, world.research().points(),
                "первая же порция обязана учитывать уже открытую технологию");
    }

    /** Начислить ровно {@code tech.cost()} очков и тут же потратить их на {@code tech} (P-02, DEV_TASKS.md). */
    private static void unlockWithPoints(World world, Tech tech) {
        world.addResearchPoints(tech.cost());
        assertTrue(world.tryUnlockTech(tech), tech + " must unlock — its prerequisites were unlocked first, in order");
    }

    /**
     * (P-01) Раньше этот тест назывался {@code acceptsChassisLikeAnyOtherTopLevelGoods} и прямо
     * утверждал «шасси стоит столько же очков, сколько шестерёнка» — это и есть дефект §2.4
     * аудита, который чинит эта задача. Шасси (глубина 66) должно давать кратно больше очков, чем
     * шестерёнка (глубина 13, база = POINTS_PER_GEAR): 10 × 66/13 ≈ 50.8 → 51, то же соотношение
     * ~5:1, что и ориентир аудита, но уже без потери точности на округлении (N15).
     */
    @Test
    void chassisEarnsMultipleTimesMoreThanGearProportionalToItsDepth() {
        World world = new World(4, 4);
        Lab lab = new Lab(RECIPES);

        assertTrue(lab.accept(world, Item.CHASSIS));
        tickUntilDone(lab, world);

        assertEquals(51, world.research().points(), "CHASSIS (глубина 66) при базе GEAR=13→10 очков даёт 51");
    }

    /** (P-01, шкала N15) Аналогично: ALLOY_GEAR (глубина 30) — 10 × 30/13 ≈ 23.1 → 23, а не «2». */
    @Test
    void alloyGearEarnsMoreThanGearProportionalToItsDepth() {
        World world = new World(4, 4);
        Lab lab = new Lab(RECIPES);

        assertTrue(lab.accept(world, Item.ALLOY_GEAR));
        tickUntilDone(lab, world);

        assertEquals(23, world.research().points());
    }

    /**
     * (P-01) Раньше буфер был просто счётчиком — не различал, ЧТО в нём лежит. Кладём подряд
     * GEAR и CHASSIS и проверяем, что каждый получает СВОЁ количество очков в порядке очереди
     * (FIFO), а не оба — среднее или оба — как первый пришедший.
     */
    @Test
    void differentQueuedItemsEachEarnTheirOwnPoints() {
        World world = new World(4, 4);
        Lab lab = new Lab(RECIPES);

        assertTrue(lab.accept(world, Item.GEAR));
        assertTrue(lab.accept(world, Item.CHASSIS));

        tickUntilDone(lab, world); // GEAR finishes first (FIFO) — +10
        assertEquals(10, world.research().points());

        tickUntilDone(lab, world); // CHASSIS finishes second — +51
        assertEquals(61, world.research().points());
    }

    /**
     * Same rule as the furnace's twin of this test (N10, NEW_BUGS_PROGRESS.md): a cooldown of 0 in
     * a save is "nothing recorded", not "the batch is ready" — a lab restored that way used to hand
     * out a research point on its very first tick.
     */
    @Test
    void aRestoredLabWithNoRecordedCooldownStartsAFullBatchInsteadOfAwardingPointsInstantly() {
        World world = new World(4, 4);
        Lab lab = new Lab(RECIPES, java.util.List.of(Item.GEAR), 0);

        lab.tick(world, 0, 0);
        assertEquals(0, world.research().points(), "one tick must not finish a whole batch");

        tickUntilDone(lab, world);
        assertEquals(10, world.research().points(), "the batch pays out after a full research time");
    }

    @Test
    void labRejectsRawMaterials() {
        World world = new World(4, 4);
        Lab lab = new Lab(RECIPES);

        assertFalse(lab.accept(world, Item.IRON_ORE));
        assertFalse(lab.accept(world, Item.IRON_PLATE));
        assertFalse(lab.accept(world, Item.BRONZE_ORE));
        assertFalse(lab.accept(world, Item.ALLOY_PLATE));
    }
}
