package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.TilePos;
import com.rustorio.BuildingType;
import com.rustorio.SaveGame;
import com.rustorio.World;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Ввод: управление камерой (скролл WASD/стрелки, драг средней кнопкой) и постройка бура.
 *
 * <p>Ввод НЕ решает, где можно строить, — он лишь сообщает миру «игрок ткнул сюда». Ставить или
 * нет (клетка на руде? свободна?) решает мир ({@link World#placeMiner}).
 */
public final class InputHandler {

    private final GameCamera camera;
    private SaveGame saveGame;

    /**
     * Прошлая точка драга средней кнопкой (валидна, только пока {@link #dragging}).
     */
    private float lastDragX;
    private float lastDragY;
    private boolean dragging;

    private BuildingType selected = BuildingType.MINER;   // с чего начинаем

    public BuildingType selected() {
        return selected;
    }   // HUD спросит, что показать

    public InputHandler(GameCamera camera) {
        this.camera = camera;
    }

    private BuildingType handleBuildSelection() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            selected = BuildingType.MINER;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            selected = BuildingType.CHEST;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            selected = BuildingType.FURNACE;
        }
        return selected;
    }

    public void handle(World world, float delta) {
        handleCamera(delta);
        TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());

        BuildingType buildingType = this.handleBuildSelection();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            world.place(buildingType, tile.x(), tile.y());
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            world.removeObject(tile.x(), tile.y());
        }

        String fileName = "/Users/liza/Downloads/saved.txt";
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            saveGame.save(world, fileName);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F6)) {
            saveGame.load(world, fileName);
        }
    }

    /**
     * Управление камерой: WASD/стрелки — скролл, зажатая средняя кнопка — драг.
     */
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
