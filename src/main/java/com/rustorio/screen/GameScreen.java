package com.rustorio.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.rustorio.core.Config;
import com.rustorio.game.GameState;
import com.rustorio.input.InputHandler;
import com.rustorio.model.World;
import com.rustorio.render.GameCamera;
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
 * жизненного цикла, которые нам не нужны.
 *
 * <p>Экран — единственное место, знающее и про ввод, и про рендер, поэтому именно
 * он создаёт камеру и раздаёт её обоим: ввод её двигает, рендер через неё смотрит.
 */
public final class GameScreen extends ScreenAdapter {

    private final GameState game;
    private final GameCamera camera;
    private final InputHandler input;
    private final Renderer renderer;
    private final Textures textures;

    public GameScreen() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        this.game = new GameState(world);
        this.camera = new GameCamera(world.width(), world.height());
        this.input = new InputHandler(camera);
        this.textures = new Textures();
        this.renderer = new Renderer(textures, camera, world);
        // Колесо мыши в libGDX — событие, опросом его не поймать: подписываемся.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                camera.zoomAt(Gdx.input.getX(), Gdx.input.getY(), amountY);
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        input.handle(game, delta);    // 1. ввод  → намерения игрока меняют мир и камеру
        game.update(delta);           // 2. апдейт → системы двигают мир по тикам
        game.prepareFrame(delta);     // 2.5. собрать слой поверх мира (панели, подсветки, тосты)
        renderer.render(game, delta); // 3. рендер → только читаем мир и рисуем
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) { // 0×0 приходит при сворачивании окна
            camera.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        renderer.dispose();
        textures.dispose();
    }
}
