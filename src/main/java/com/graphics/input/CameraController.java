package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;

/**
 * Управление камерой: WASD/стрелки — скролл, зажатая средняя кнопка — драг. Вынесено из {@link
 * InputHandler} (P3-07, BUG_FIX_PROGRESS.md) — камера не знает и не должна знать о зданиях,
 * истории действий или паузе, так что ей незачем сидеть в одном классе с этим.
 */
final class CameraController {

    private final GameCamera camera;

    /** Прошлая точка драга средней кнопкой (валидна, только пока {@link #dragging}). */
    private float lastDragX;
    private float lastDragY;
    private boolean dragging;

    CameraController(GameCamera camera) {
        this.camera = camera;
    }

    void handle(float delta) {
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
