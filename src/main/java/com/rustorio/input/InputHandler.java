package com.rustorio.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.rustorio.core.Config;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Building;
import com.rustorio.render.GameCamera;

/**
 * Фаза 1 игрового цикла: ввод.
 *
 * <p>Единственная задача — превратить действия игрока (клавиши, мышь) в изменения
 * состояния. Здесь НИЧЕГО не рисуется. Состояние меняем только через методы
 * {@link GameState}, камеру — через {@link GameCamera}.
 *
 * <p>Класс хранит память между кадрами (прошлая точка драга средней кнопкой) —
 * поэтому это обычный объект, который создаёт экран.
 *
 * <p>Зависимость {@code input → render} законна: обе — детали «слоя движка», а
 * архитектурные правила запрещают только домену знать о них (и циклы пакетов;
 * {@code render} про {@code input} не знает — цикла нет).
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

    public void handle(GameState game, float delta) {
        handleCamera(delta);

        // Клетка под курсором — через камеру: мышь живёт в пикселях окна, а клетка —
        // в мире, и только камера знает, куда сейчас смотрит окно и с каким зумом.
        game.setHover(camera.pickTile(Gdx.input.getX(), Gdx.input.getY(), game.world()));

        // Пауза.
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            game.togglePause();
        }

        // Выбор инструмента: идём по СПИСКУ инструментов, а не по руками написанной
        // лесенке. Номер слота объявлен в самом Tool, и забыть его нельзя.
        for (Tool tool : Tool.values()) {
            if (Gdx.input.isKeyJustPressed(keyForSlot(tool.hotkeySlot()))) {
                game.selectTool(tool);
            }
        }

        // Поворот направления постройки.
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            game.rotateDirection();
        }

        // Строительство и снос. Кнопки ОПРАШИВАЮТСЯ каждый кадр (isButtonPressed):
        // ведёшь зажатую мышь — рисуешь линию. От пересоздания на месте защищает
        // правило sameKind в World.place — ввод может позволить себе быть простым.
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            game.hover().ifPresent(cell -> game.world().place(cell.x(), cell.y(),
                    Building.create(game.tool(), game.direction())));
        }
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
            game.hover().ifPresent(cell -> game.world().remove(cell.x(), cell.y()));
        }
    }

    /**
     * Управление камерой: WASD/стрелки — скролл, зажатая средняя кнопка — драг.
     * Зум колесом сюда не попадает: колесо в libGDX — событие, а не состояние,
     * его ловит {@code InputProcessor} на экране и отдаёт камере напрямую.
     */
    private void handleCamera(float delta) {
        float step = Config.CAMERA_PAN_SPEED * delta;
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
                // Мир должен ехать ЗА курсором, поэтому сдвиг камеры — с обратным
                // знаком; мышиный Y растёт вниз, мировой — вверх, отсюда разные знаки.
                camera.pan(lastDragX - mx, my - lastDragY);
            }
            lastDragX = mx;
            lastDragY = my;
            dragging = true;
        } else {
            dragging = false;
        }
    }

    /**
     * Номер слота (1..9) → код клавиши libGDX.
     *
     * <p>Перевод живёт ЗДЕСЬ, а не в {@code Tool}: {@code Tool} лежит в {@code core},
     * которому запрещено знать про движок. Слой ввода про движок знать обязан.
     */
    private static int keyForSlot(int slot) {
        return Input.Keys.NUM_0 + slot;
    }
}
