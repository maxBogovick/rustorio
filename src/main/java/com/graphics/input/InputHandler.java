package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.TilePos;
import com.rustorio.ActionHistory;
import com.rustorio.BuildingType;
import com.rustorio.Direction;
import com.rustorio.PlaceAction;
import com.rustorio.RemoveAction;
import com.rustorio.SaveGame;
import com.rustorio.UpgradeSpeedAction;
import com.rustorio.World;

/**
 * Ввод: управление камерой (скролл WASD/стрелки, драг средней кнопкой) и постройка зданий.
 *
 * <p>Раньше на каждое здание был свой жест (ЛКМ/ПКМ/F) — и это упёрлось в потолок. Теперь игрок
 * СНАЧАЛА ВЫБИРАЕТ здание клавишами 1/2/3, а строит всегда одинаково: ЛКМ ставит выбранное, ПКМ
 * сносит. Один жест постройки на любое число зданий.
 *
 * <p>ЛКМ/ПКМ больше не зовут мир напрямую — они заворачивают намерение в {@link PlaceAction}/
 * {@link RemoveAction} и отдают {@link ActionHistory} (урок 15): так у отмены (Ctrl+Z) и повтора
 * (Ctrl+Y) есть что откатывать. Ввод НЕ решает, где можно строить, — он лишь сообщает миру
 * «игрок ткнул сюда выбранным зданием». Ставить или нет (клетка на руде? свободна?) решает мир
 * ({@link World#place}): правило места — про мир, а не про кнопку. Зум колесом ловит экран
 * ({@code InputProcessor}) и отдаёт камере: колесо в libGDX — событие, а не состояние.
 */
public final class InputHandler {

    private final GameCamera camera;
    private final ActionHistory history = new ActionHistory();

    /** Что игрок сейчас строит. ЛКМ ставит именно это; меняется клавишами 1/2/3. */
    private BuildingType selected = BuildingType.MINER;

    /** Куда повёрнута следующая постройка — важно только ленте (урок 19). Меняется клавишей R. */
    private Direction facing = Direction.RIGHT;

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

    /** Куда повёрнута следующая постройка — HUD показывает это игроку. */
    public Direction facing() {
        return facing;
    }

    public void handle(World world, float delta) {
        handleCamera(delta);
        handleBuildSelection();

        // Один жест постройки: ЛКМ — поставить выбранное здание, ПКМ — снести. Оба заворачиваются
        // в действие и идут через историю — так их можно отменить.
        // Момент нажатия, не «зажато»: один клик — одно действие.
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            history.perform(world, new PlaceAction(selected, tile.x(), tile.y(), facing));
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            history.perform(world, new RemoveAction(tile.x(), tile.y()));
        }

        // Поворот следующей постройки (урок 19): R крутит направление ленты по кругу.
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            facing = facing.rotate();
        }

        // Модуль скорости (урок 18): U под курсором ускоряет здание вдвое. Тоже через историю —
        // отмена снимает обёртку, как и было задумано в UpgradeSpeedAction.
        if (Gdx.input.isKeyJustPressed(Input.Keys.U)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            history.perform(world, new UpgradeSpeedAction(tile.x(), tile.y()));
        }

        // Отмена/повтор (урок 15): Ctrl+Z откатывает последнее действие, Ctrl+Y повторяет откаченное.
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            history.undo(world);
        }
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            history.redo(world);
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
