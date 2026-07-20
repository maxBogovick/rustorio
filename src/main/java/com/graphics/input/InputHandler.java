package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.TilePos;
import com.rustorio.World;

/**
 * Ввод: управление камерой (скролл WASD/стрелки, драг средней кнопкой) и постройка бура.
 *
 * <p>Ввод НЕ решает, где можно строить, — он лишь сообщает миру «игрок ткнул сюда». Ставить или
 * нет (клетка на руде? свободна?) решает мир ({@link World#placeMiner}).
 */
public final class InputHandler {

    private final GameCamera camera;

    /** Прошлая точка драга средней кнопкой (валидна, только пока {@link #dragging}). */
    private float lastDragX;
    private float lastDragY;
    private boolean dragging;

    public InputHandler(GameCamera camera) {
        this.camera = camera;
    }

    public void handle(World world, float delta) {
        handleCamera(delta);

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            world.placeMiner(tile.x(), tile.y());
        }
    }

    /** Управление камерой: WASD/стрелки — скролл, зажатая средняя кнопка — драг. */
    private void handleCamera(float delta) {
        float step = GfxConfig.CAMERA_PAN_SPEED * delta;
        float dx = 0f;
        float dy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            dy += step;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            dy -= step;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            dx -= step;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            dx += step;
        }
        if (dx != 0f || dy != 0f) {
            camera.pan(dx, dy);
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.MIDDLE)) {
            float mx = Gdx.input.getX();
            float my = Gdx.input.getY();
            if (dragging) {
                // Мир едет ЗА курсором: сдвиг камеры с обратным знаком; мышиный Y растёт вниз,
                // мировой — вверх, отсюда разные знаки.
                camera.pan(lastDragX - mx, my - lastDragY);
            }
            lastDragX = mx;
            lastDragY = my;
            dragging = true;
        } else {
            dragging = false;
        }
    }
}
