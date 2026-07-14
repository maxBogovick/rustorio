package com.rustorio.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.rustorio.core.Config;
import com.rustorio.game.GameState;
import com.rustorio.input.InputHandler;
import com.rustorio.model.World;
import com.rustorio.render.Renderer;
import com.rustorio.render.Textures;

/**
 * Экран самой игры — здесь «крутится» игровой цикл.
 *
 * <p>libGDX сам вызывает {@link #render(float)} каждый кадр; мы держим внутри
 * те же три расцепленные фазы, что и в Rust-версии:
 * <pre>
 *     input → update → render
 * </pre>
 * Наследуемся от {@link ScreenAdapter}, чтобы не реализовывать пустыми методы
 * жизненного цикла, которые нам не нужны (показ/скрытие/пауза).
 *
 * <p>Почему {@code Screen}, а не голый {@code ApplicationAdapter}: экраны —
 * штатный способ libGDX разбивать игру на состояния (меню, игра, пауза). Сейчас
 * экран один, но добавить меню позже — это новый {@code Screen}, без переделки
 * цикла (рекомендация libGDX по структуре проекта).
 */
public final class GameScreen extends ScreenAdapter {

    private final GameState game;
    private final Renderer renderer;
    private final Textures textures;

    public GameScreen() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        this.game = new GameState(world);
        this.textures = new Textures();
        this.renderer = new Renderer(textures);
    }

    @Override
    public void render(float delta) {
        InputHandler.handle(game);   // 1. ввод  → намерения игрока меняют мир
        game.update(delta);          // 2. апдейт → системы двигают мир по тикам
        renderer.render(game, delta); // 3. рендер → только читаем мир и рисуем
    }

    @Override
    public void dispose() {
        renderer.dispose();
        textures.dispose();
    }
}
