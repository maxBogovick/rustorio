package com.rustorio.sim;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты ПЕРЕДАЧИ предметов между зданиями (L4): двухфазный тик и штамп версии.
 */
class TransferTest {

    /** Прогнать N тиков симуляции над этим миром. */
    private static void stepTimes(World world, int n) {
        Simulation simulation = new Simulation(world);
        TickContext ctx = new TickContext(Config.TICK);
        for (int i = 0; i < n; i++) {
            simulation.step(ctx);
        }
    }

    @Test
    @DisplayName("Бур наполняет соседний ящик")
    void minerFillsAdjacentChest() {
        World world = World.generate(20, 20);
        assertTrue(world.tile(6, 5).hasOre(), "предусловие: под (6,5) руда");
        world.place(6, 5, Building.create(Tool.MINER, Direction.EAST));
        world.place(7, 5, Building.create(Tool.CHEST, Direction.EAST));

        stepTimes(world, 60);

        Chest chest = (Chest) world.tile(7, 5).building();
        assertTrue(chest.items() >= 1, "ящик должен получить хотя бы один предмет");
    }

    @Test
    @DisplayName("Бур без руды под собой ничего не отдаёт")
    void minerWithoutOreProducesNothing() {
        World world = World.generate(20, 20);
        assertFalse(world.tile(0, 0).hasOre(), "предусловие: под (0,0) руды нет");
        world.place(0, 0, Building.create(Tool.MINER, Direction.EAST));
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST));

        stepTimes(world, 60);

        assertEquals(0, ((Chest) world.tile(1, 0).building()).items());
    }

    @Test
    @DisplayName("Штамп версии: за один тик клетку столбит ровно один сосед")
    void claimAllowsOnlyOneClaimPerCellPerTick() {
        World world = World.generate(5, 5);
        world.beginTick();
        // Оба претендуют на клетку (2,1): справа-налево и слева-направо.
        assertTrue(world.claimNeighbor(1, 1, Direction.EAST), "первый сосед столбит (2,1)");
        assertFalse(world.claimNeighbor(3, 1, Direction.WEST), "второй в тот же тик — уже занято");
        // Новый тик — прошлые штампы устарели.
        world.beginTick();
        assertTrue(world.claimNeighbor(3, 1, Direction.WEST), "на новом тике клетка снова свободна");
    }
}
