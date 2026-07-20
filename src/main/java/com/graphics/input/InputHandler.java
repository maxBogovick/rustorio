package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.TilePos;
import com.rustorio.BuildingType;
import com.rustorio.SaveGame;
import com.rustorio.World;

/**
 * Ввод: управление камерой (скролл WASD/стрелки, драг средней кнопкой) и постройка зданий.
 *
 * <p>Раньше на каждое здание был свой жест (ЛКМ/ПКМ/F) — и это упёрлось в потолок. Теперь игрок
 * СНАЧАЛА ВЫБИРАЕТ здание клавишами 1/2/3, а строит всегда одинаково: ЛКМ ставит выбранное, ПКМ
 * сносит. Один жест постройки на любое число зданий.
 *
 * <p>Ввод НЕ решает, где можно строить, — он лишь сообщает миру «игрок ткнул сюда выбранным
 * зданием». Ставить или нет (клетка на руде? свободна?) решает мир ({@link World#place}): правило
 * места — про мир, а не про кнопку. Зум колесом ловит экран ({@code InputProcessor}) и отдаёт
 * камере: колесо в libGDX — событие, а не состояние.
 */
public final class InputHandler {

    private final GameCamera camera;

    /** Что игрок сейчас строит. ЛКМ ставит именно это; меняется клавишами 1/2/3. */
    private BuildingType selected = BuildingType.MINER;

    /** Прошлая точка драга средней кнопкой (валидна, только пока {@link #dragging}). */
    private float lastDragX;
    private float lastDragY;
    private boolean dragging;

    public InputHandler(GameCamera camera) {
        this.camera = camera;
    }

    /** Что выбрано в панели постройки — HUD показывает это игроку. */
    public BuildingType selected() {
        return selected;
    }

    public void handle(World world, float delta) {
        handleCamera(delta);
        handleBuildSelection();

        // Один жест постройки: ЛКМ — поставить выбранное здание, ПКМ — снести.
        // Момент нажатия, не «зажато»: один клик — одно действие.
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            world.place(selected, tile.x(), tile.y());
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            world.removeBuilding(tile.x(), tile.y());
        }

        // Сохранение/загрузка (урок 10): F5 — записать мир на диск, F9 — прочитать обратно.
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            SaveGame.save(world, SaveGame.DEFAULT_PATH);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            SaveGame.load(world, SaveGame.DEFAULT_PATH);
        }
    }

    /**
     * Клавиши 1..N выбирают здание из панели — номер клавиши = порядок в {@link BuildingType}
     * (урок 13). {@code Input.Keys.NUM_1..NUM_9} в libGDX идут подряд, поэтому «клавиша под
     * номером» — это просто {@code NUM_1 + ordinal}, без явного перечисления каждой.
     */
    private void handleBuildSelection() {
        for (BuildingType type : BuildingType.values()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + type.ordinal())) {
                selected = type;
            }
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
