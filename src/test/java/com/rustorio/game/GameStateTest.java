package com.rustorio.game;

import com.rustorio.core.Direction;
import com.rustorio.core.Tool;
import com.rustorio.model.Building;
import com.rustorio.model.Miner;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Продвижение времени: «фиксированный тик + аккумулятор». Проверяем GameState.update. */
class GameStateTest {

    /** Мир 20×20 с буром на залежи (6,5). */
    private static GameState minerOnOre() {
        World world = World.generate(20, 20);
        world.place(6, 5, Building.create(Tool.MINER, Direction.EAST));
        return new GameState(world);
    }

    private static Miner miner(GameState game) {
        return (Miner) game.world().tile(6, 5).building();
    }

    @Test
    @DisplayName("update продвигает симуляцию: набежавшего времени хватает на тик")
    void updateAdvancesSimulation() {
        GameState game = minerOnOre();
        game.update(1.0f);
        assertTrue(miner(game).progressFraction() > 0f, "бур должен продвинуться хотя бы за тик");
    }

    @Test
    @DisplayName("На паузе update не двигает мир")
    void pausedUpdateDoesNothing() {
        GameState game = minerOnOre();
        game.togglePause();
        game.update(1.0f);
        assertEquals(0f, miner(game).progressFraction(), 1e-6f, "на паузе бур стоит");
    }

    @Test
    @DisplayName("Мелкие кадры не дотягивают до тика — мир стоит")
    void subTickFrameDoesNotStep() {
        GameState game = minerOnOre();
        game.update(0.05f);
        assertEquals(0f, miner(game).progressFraction(), 1e-6f, "за неполный тик мир не двигается");
    }
}
