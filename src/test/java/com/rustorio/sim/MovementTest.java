package com.rustorio.sim;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты ДВИЖЕНИЯ предметов по лентам (L5) — стык «лента ↔ соседнее здание» через
 * протокол передачи L4.
 *
 * <p><b>Третий тест L5 отсюда убран в L6.</b> Он проверял, что из двух ЛЕНТ, толкающих
 * в одну клетку, выигрывает ровно одна (штамп {@code claim}). Но с L6 два соседних
 * тайла ленты ОДНОГО направления — это уже не два независимых здания, а один и тот же
 * {@code BeltSegment}: сценарий «конкурируют» для них физически не собрать. Тест не
 * стал ошибочным — он молча проверял бы уже другую, вырожденную ситуацию. Сам механизм
 * «штамп на клетку — только один сосед» никуда не делся и по-прежнему проверяется
 * {@code TransferTest.claimAllowsOnlyOneClaimPerCellPerTick} (L4) — там конкурируют
 * bur и лента, а не два тайла одной линии.
 */
class MovementTest {

    private static void step(World world) {
        new Simulation(world).step(new TickContext(Config.TICK));
    }

    private static void stepTimes(World world, int n) {
        Simulation simulation = new Simulation(world);
        TickContext ctx = new TickContext(Config.TICK);
        for (int i = 0; i < n; i++) {
            simulation.step(ctx);
        }
    }

    @Test
    @DisplayName("Цепочка бур → лента → лента → ящик доставляет руду")
    void minerBeltChestDelivers() {
        World world = World.generate(20, 20);
        int x = 6, y = 5; // центр залежи руды
        assertTrue(world.tile(x, y).hasOre(), "предусловие: под (6,5) руда");
        world.place(x, y, Building.create(Tool.MINER, Direction.EAST));
        world.place(x + 1, y, Building.create(Tool.BELT, Direction.EAST));
        world.place(x + 2, y, Building.create(Tool.BELT, Direction.EAST));
        world.place(x + 3, y, Building.create(Tool.CHEST, Direction.EAST));

        stepTimes(world, 60);

        assertTrue(((Chest) world.tile(x + 3, y).building()).items() >= 1,
                "ящик за цепочкой лент должен получить хотя бы один предмет");
    }

    @Test
    @DisplayName("Передача по ленте — это перемещение, а не копирование (ровно на клетку)")
    void itemMovesExactlyOneCellPerTick() {
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
}
