package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Туннель на границе {@link BeltSegment}: вход берёт от обычного сегмента, выход отдаёт в
 * обычный сегмент дальше — граница, которую {@link Belt#tick} теперь проходит не сам по себе,
 * а через свой сегмент; тест проверяет, что это не изменило поведение на глазах у туннеля.
 */
class UndergroundBeltTest {

    @Test
    void beltFeedsTunnelWhichExitsToBeltOnTheOtherSide() {
        World world = new World(10, 10);
        world.placeBelt(0, 0, Direction.RIGHT);
        world.placeUndergroundIn(1, 0, Direction.RIGHT);
        // (2,0) и (3,0) пусты — туннель ныряет под ними, в пределах MAX_RANGE = 4.
        world.placeUndergroundOut(4, 0, Direction.RIGHT);
        world.placeBelt(5, 0, Direction.RIGHT);
        Chest chest = new Chest();
        world.restore(6, 0, chest);

        Belt feed = (Belt) world.peek(0, 0);
        assertTrue(feed.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 10; i++) {
            world.tick();
        }
        assertEquals(1, chest.count());
    }

    @Test
    void tunnelWithoutPartnerWithinRangeNeverDelivers() {
        World world = new World(10, 10);
        world.placeUndergroundIn(0, 0, Direction.RIGHT);
        // Пара дальше MAX_RANGE (4) — с учётом самой клетки входа это x=6, шаг 6.
        world.placeUndergroundOut(6, 0, Direction.RIGHT);

        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0);
        assertTrue(in.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 5; i++) {
            world.tick();
        }
        assertEquals(Item.IRON_ORE, in.heldItem(), "пара вне дальности — груз остаётся ждать на входе");
    }

    @Test
    void tunnelFindsPartnerEvenWhenOutIsUpgradedWithSpeedModule() {
        World world = new World(10, 10);
        world.placeUndergroundIn(0, 0, Direction.RIGHT);
        world.placeUndergroundOut(3, 0, Direction.RIGHT);
        // Апгрейд выхода (клавиша U, см. UpgradeSpeedAction) кладёт SpeedModule поверх — без
        // Building.unwrap в findPartner вход решил бы, что пары больше нет вовсе, и туннель
        // сломался бы НАВСЕГДА, а не просто перестал бы подсвечиваться (см. её javadoc).
        world.restore(3, 0, new SpeedModule(world.removeBuilding(3, 0)));
        world.placeBelt(4, 0, Direction.RIGHT);
        Chest chest = new Chest();
        world.restore(5, 0, chest);

        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0);
        assertTrue(in.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 8; i++) {
            world.tick();
        }
        assertEquals(1, chest.count(), "апгрейженный выход туннеля всё ещё находится и работает");
    }
}
