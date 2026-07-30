package com.graphics.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.graphics.GfxConfig;
import com.graphics.input.InputHandler;
import com.graphics.render.GameCamera;
import com.graphics.render.Renderer;
import com.graphics.render.Textures;
import com.rustorio.BuildingType;
import com.rustorio.World;

/**
 * Экран игры: держит мир и каждый кадр гоняет связку ввод → тик → рендер.
 *
 * <p>Мир пока минимальный (карта с рудой + поставленные буры). libGDX сам вызывает
 * {@link #render(float)} каждый кадр.
 */
public final class GameScreen extends ScreenAdapter {

    private final World world;
    private final GameCamera camera;
    private final InputHandler input;
    private final Renderer renderer;
    private final Textures textures;

    public GameScreen() {
        this.world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H);
        this.camera = new GameCamera(GfxConfig.GRID_W, GfxConfig.GRID_H);
        this.input = new InputHandler(camera);
        this.textures = new Textures();
        this.renderer = new Renderer(textures, camera);
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
        input.handle(world, delta);
        world.tick();
        renderer.render(world, delta, input.selected());
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
