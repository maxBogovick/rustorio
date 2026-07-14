package com.rustorio.game;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import com.rustorio.model.Belt;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты продвижения времени: приём «фиксированный тик + аккумулятор». Проверяем
 * именно {@link GameState#update(float)}, а не {@code Systems.step} напрямую.
 */
class GameStateTest {

    /** Мир 3×1 с предметом на левой ленте; всё едет вправо. */
    private static GameState twoBeltsWithItem() {
        World world = World.generate(3, 1);
        world.place(0, 0, Building.create(Tool.BELT, Direction.EAST));
        world.place(1, 0, Building.create(Tool.BELT, Direction.EAST));
        ((Belt) world.tile(0, 0).building()).accept(Item.IRON_ORE);
        return new GameState(world);
    }

    @Test
    @DisplayName("update продвигает симуляцию: набежавшего времени хватает на тик")
    void updateAdvancesSimulation() {
        GameState game = twoBeltsWithItem();
        // Большой delta ограничивается MAX_FRAME_TIME сверху и даёт хотя бы один тик.
        game.update(1.0f);
        assertTrue(((Belt) game.world().tile(1, 0).building()).item().isPresent(),
                "предмет должен переехать на соседнюю ленту хотя бы за один тик");
    }

    @Test
    @DisplayName("На паузе update не двигает мир")
    void pausedUpdateDoesNothing() {
        GameState game = twoBeltsWithItem();
        game.togglePause();
        game.update(1.0f);
        assertTrue(((Belt) game.world().tile(0, 0).building()).item().isPresent(),
                "на паузе предмет должен остаться на месте");
        assertFalse(((Belt) game.world().tile(1, 0).building()).item().isPresent(),
                "на паузе сосед не должен ничего получить");
    }

    @Test
    @DisplayName("Мелкие кадры не дотягивают до тика — мир стоит")
    void subTickFrameDoesNotStep() {
        GameState game = twoBeltsWithItem();
        // Один кадр короче TICK (0.18с) — накопленного времени не хватает на шаг.
        game.update(0.05f);
        assertFalse(((Belt) game.world().tile(1, 0).building()).item().isPresent(),
                "за неполный тик мир двигаться не должен");
    }

    @Test
    @DisplayName("Смена инструмента и поворот направления отражаются в состоянии")
    void toolAndDirectionMutators() {
        GameState game = twoBeltsWithItem();
        game.selectTool(Tool.FURNACE);
        game.rotateDirection(); // EAST → SOUTH
        game.setHover(new Cell(2, 0));
        assertTrue(game.tool() == Tool.FURNACE);
        assertTrue(game.direction() == Direction.SOUTH);
        assertTrue(game.hover().isPresent());
    }
}
