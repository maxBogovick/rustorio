package com.rustorio.sim;

import com.rustorio.core.Balance;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import com.rustorio.model.Belt;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты ПЕРЕДАЧИ предметов между зданиями (трек B).
 *
 * <p>Раньше это был один файл {@code SystemsTest}, проверявший и передачу, и работу
 * печи со сборщиком. Так он принадлежал бы сразу двум трекам — а у нас правило «один
 * файл — один хозяин». Логика машин переехала в {@code MachinesTest} (трек C).
 */
class MovementTest {

    static TickContext ctx() {
        return new TickContext(Config.TICK, new Balance());
    }

    /** Один шаг новой симуляции над этим миром. */
    static void step(World world) {
        new Simulation(world).step(ctx());
    }

    /** Прогнать N тиков симуляции. */
    static void stepTimes(World world, int n) {
        Simulation simulation = new Simulation(world);
        TickContext ctx = ctx();
        for (int i = 0; i < n; i++) {
            simulation.step(ctx);
        }
    }

    @Test
    @DisplayName("Цепочка бур→лента→ящик доставляет предметы")
    void minerBeltChestDelivers() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        int x = 6, y = 5; // центр залежи руды из генерации карты
        assertTrue(world.tile(x, y).hasOre(), "предусловие: под (6,5) должна быть руда");

        world.place(x, y, Building.create(Tool.MINER, Direction.EAST));
        world.place(x + 1, y, Building.create(Tool.BELT, Direction.EAST));
        world.place(x + 2, y, Building.create(Tool.CHEST, Direction.EAST));
        stepTimes(world, 60);

        Chest chest = assertInstanceOf(Chest.class, world.tile(x + 2, y).building());
        assertTrue(chest.items() >= 1, "ящик должен получить хотя бы один предмет");
    }

    @Test
    @DisplayName("Бур без руды под собой ничего не производит")
    void minerWithoutOreProducesNothing() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        assertFalse(world.tile(0, 0).hasOre(), "предусловие: под (0,0) руды нет");

        world.place(0, 0, Building.create(Tool.MINER, Direction.EAST));
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST));
        stepTimes(world, 60);

        Chest chest = assertInstanceOf(Chest.class, world.tile(1, 0).building());
        assertEquals(0, chest.items());
    }

    @Test
    @DisplayName("Передача предмета — это перемещение, а не копирование")
    void itemMovesExactlyOnce() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.BELT, Direction.EAST));
        world.place(1, 0, Building.create(Tool.BELT, Direction.EAST));
        ((Belt) world.tile(0, 0).building()).accept(Item.IRON_ORE);

        step(world);

        assertTrue(((Belt) world.tile(0, 0).building()).item().isEmpty(),
                "источник должен опустеть");
        assertEquals(Item.IRON_ORE, ((Belt) world.tile(1, 0).building()).item().orElse(null),
                "приёмник должен получить ровно тот же предмет");
    }

    @Test
    @DisplayName("Две ленты в одну клетку пропихнут ровно один предмет")
    void twoBeltsIntoOneMoveOnlyOne() {
        World world = World.generate(3, 1);
        world.place(0, 0, Building.create(Tool.BELT, Direction.EAST)); // → (1,0)
        world.place(1, 0, Building.create(Tool.BELT, Direction.EAST));
        world.place(2, 0, Building.create(Tool.BELT, Direction.WEST)); // → (1,0)
        ((Belt) world.tile(0, 0).building()).accept(Item.IRON_ORE);
        ((Belt) world.tile(2, 0).building()).accept(Item.IRON_PLATE);

        step(world);

        assertEquals(2, world.belts().itemCount(),
                "предмет не должен продублироваться или потеряться");
        assertTrue(((Belt) world.tile(1, 0).building()).item().isPresent(),
                "средняя лента должна получить один предмет");
    }
}
