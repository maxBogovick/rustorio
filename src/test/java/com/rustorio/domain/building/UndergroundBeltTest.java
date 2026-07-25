package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Туннель на границе {@link BeltSegment}: вход берёт от обычного сегмента, выход отдаёт в
 * обычный сегмент дальше.
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
        world.restoreBuilding(6, 0, chest);

        Belt feed = (Belt) world.peek(0, 0).orElseThrow();
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

        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0).orElseThrow();
        assertTrue(in.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 5; i++) {
            world.tick();
        }
        assertEquals(Optional.of(Item.IRON_ORE), in.heldItem(), "пара вне дальности — груз остаётся ждать на входе");
    }

    @Test
    void tunnelFindsPartnerEvenWhenOutIsUpgradedWithSpeedModule() {
        World world = new World(10, 10);
        world.placeUndergroundIn(0, 0, Direction.RIGHT);
        world.placeUndergroundOut(3, 0, Direction.RIGHT);
        // Апгрейд выхода (клавиша U) кладёт SpeedModule поверх — без Building.unwrap в
        // findPartner вход решил бы, что пары больше нет вовсе, и туннель сломался бы НАВСЕГДА.
        world.restoreBuilding(3, 0, new SpeedModule(world.removeBuilding(3, 0).orElseThrow()));
        world.placeBelt(4, 0, Direction.RIGHT);
        Chest chest = new Chest();
        world.restoreBuilding(5, 0, chest);

        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0).orElseThrow();
        assertTrue(in.accept(world, Item.IRON_ORE));

        for (int i = 0; i < 8; i++) {
            world.tick();
        }
        assertEquals(1, chest.count(), "апгрейженный выход туннеля всё ещё находится и работает");
    }
}
