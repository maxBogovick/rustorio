package com.rustorio.sim;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.rustorio.sim.MovementTest.stepTimes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты логистики: развилка и подземная лента (задача B4, трек B).
 */
class LogisticsTest {

    // ── Сплиттер ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Развилка раздаёт предметы по очереди, а не валит в один выход")
    void splitterAlternatesBetweenOutputs() {
        // Сплиттер на (1,1) смотрит на восток. Выходы: восток, юг (право), север (лево).
        World world = World.generate(5, 5);
        world.place(1, 1, Building.create(Tool.SPLITTER, Direction.EAST));
        world.place(2, 1, Building.create(Tool.CHEST, Direction.EAST)); // прямо
        world.place(1, 2, Building.create(Tool.CHEST, Direction.EAST)); // направо (юг)
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST)); // налево (север)

        Splitter splitter = (Splitter) world.tile(1, 1).building();
        for (int i = 0; i < 6; i++) {
            splitter.accept(Item.IRON_ORE);
            stepTimes(world, 1);
        }

        int east = ((Chest) world.tile(2, 1).building()).items();
        int south = ((Chest) world.tile(1, 2).building()).items();
        int north = ((Chest) world.tile(1, 0).building()).items();

        assertEquals(6, east + south + north, "ни один предмет не потерялся");
        assertEquals(2, east, "поток разделился поровну между тремя выходами");
        assertEquals(2, south);
        assertEquals(2, north);
    }

    @Test
    @DisplayName("Заблокированный выход пропускается — поток идёт в свободный")
    void splitterSkipsBlockedOutput() {
        // Единственный сосед — ящик на востоке. Остальные стороны пусты (стена).
        World world = World.generate(5, 5);
        world.place(1, 1, Building.create(Tool.SPLITTER, Direction.EAST));
        world.place(2, 1, Building.create(Tool.CHEST, Direction.EAST));

        Splitter splitter = (Splitter) world.tile(1, 1).building();
        for (int i = 0; i < 5; i++) {
            splitter.accept(Item.IRON_ORE);
            stepTimes(world, 1);
        }

        assertEquals(5, ((Chest) world.tile(2, 1).building()).items(),
                "все пять предметов ушли в единственный свободный выход, без потери тиков");
    }

    @Test
    @DisplayName("Все выходы заняты — предмет остаётся в развилке (затор виден)")
    void splitterHoldsItemWhenEverythingIsBlocked() {
        World world = World.generate(5, 5);
        world.place(1, 1, Building.create(Tool.SPLITTER, Direction.EAST)); // соседей нет вообще

        Splitter splitter = (Splitter) world.tile(1, 1).building();
        splitter.accept(Item.IRON_ORE);
        stepTimes(world, 5);

        assertTrue(splitter.held().isPresent(), "деть предмет некуда — он обязан остаться");
        assertFalse(splitter.canAccept(Item.IRON_ORE), "и второй предмет развилка не примет");
    }

    // ── Подземка ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Подземка доставляет предмет через несколько клеток")
    void undergroundDeliversItemAcrossGap() {
        World world = World.generate(10, 1);
        world.place(0, 0, Building.create(Tool.UNDERGROUND, Direction.EAST)); // вход
        world.place(4, 0, Building.create(Tool.UNDERGROUND, Direction.EAST)); // выход
        world.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));

        UndergroundBelt entry = (UndergroundBelt) world.tile(0, 0).building();
        stepTimes(world, 1); // симуляция находит пару и раздаёт роли
        assertTrue(entry.canAccept(Item.IRON_ORE), "вход должен принимать");
        entry.accept(Item.IRON_ORE);

        stepTimes(world, 20);

        assertEquals(1, ((Chest) world.tile(5, 0).building()).items(),
                "предмет обязан вынырнуть за выходом и попасть в ящик");
    }

    @Test
    @DisplayName("Подземка НЕ быстрее обычной ленты — иначе вся игра уехала бы под землю")
    void undergroundIsNotFasterThanBelt() {
        // Земля: 4 клетки ленты. Подземка: вход на (0,0), выход на (4,0) — те же 4 клетки.
        World ground = World.generate(10, 1);
        for (int x = 0; x <= 4; x++) {
            ground.place(x, 0, Building.create(Tool.BELT, Direction.EAST));
        }
        ground.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));
        ((com.rustorio.model.Belt) ground.tile(0, 0).building()).accept(Item.IRON_ORE);

        World under = World.generate(10, 1);
        under.place(0, 0, Building.create(Tool.UNDERGROUND, Direction.EAST));
        under.place(4, 0, Building.create(Tool.UNDERGROUND, Direction.EAST));
        under.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));
        stepTimes(under, 1);
        ((UndergroundBelt) under.tile(0, 0).building()).accept(Item.IRON_ORE);

        int groundTicks = ticksUntilDelivered(ground, 5, 0);
        int underTicks = ticksUntilDelivered(under, 5, 0);

        assertTrue(underTicks >= groundTicks,
                "подземка (" + underTicks + " тиков) не должна обгонять ленту ("
                        + groundTicks + " тиков): она удобство, а не читерство");
    }

    @Test
    @DisplayName("Подземка без пары не работает вовсе")
    void loneUndergroundDoesNothing() {
        World world = World.generate(10, 1);
        world.place(0, 0, Building.create(Tool.UNDERGROUND, Direction.EAST)); // пары нет

        UndergroundBelt lone = (UndergroundBelt) world.tile(0, 0).building();
        stepTimes(world, 3);

        assertEquals(UndergroundBelt.Role.NONE, lone.role());
        assertFalse(lone.canAccept(Item.IRON_ORE), "одиночная подземка не принимает предметы");
    }

    /** Через сколько тиков в ящике появится предмет. */
    private static int ticksUntilDelivered(World world, int chestX, int chestY) {
        Chest chest = (Chest) world.tile(chestX, chestY).building();
        for (int tick = 1; tick <= 200; tick++) {
            stepTimes(world, 1);
            if (chest.items() > 0) {
                return tick;
            }
        }
        throw new AssertionError("предмет так и не доехал за 200 тиков");
    }
}
