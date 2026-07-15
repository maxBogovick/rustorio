package com.rustorio.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.rustorio.core.Config;
import com.rustorio.core.Tech;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.game.action.CompositeAction;
import com.rustorio.game.action.PlaceBuilding;
import com.rustorio.game.action.PlayerAction;
import com.rustorio.game.action.RemoveBuilding;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.persist.LoadService;
import com.rustorio.persist.SaveService;
import com.rustorio.render.GameCamera;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Фаза 1 игрового цикла: ввод.
 *
 * <p>Единственная задача — превратить действия игрока (клавиши, мышь) в изменения
 * состояния. Здесь НИЧЕГО не рисуется. Мир меняем только через методы
 * {@link GameState}/{@link com.rustorio.model.World}, камеру — через {@link GameCamera}.
 *
 * <p>Раньше класс был статическим и без состояния. Камера добавила ему память между
 * кадрами (прошлая точка драга средней кнопкой) — поэтому теперь это обычный объект,
 * который создаёт экран.
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

    /** Текущий штрих ЛКМ (постройка) и ПКМ (снос) — каждый копится и коммитится разом. */
    private final Stroke placeStroke = new Stroke(true);
    private final Stroke removeStroke = new Stroke(false);

    /** Сохранение/загрузка. Путь фиксирован — рабочая папка проекта (см. build.gradle). */
    private final SaveService saveService = new SaveService();
    private final LoadService loadService = new LoadService();
    private static final Path SAVE_PATH = Path.of("rustorio-save.json");

    public InputHandler(GameCamera camera) {
        this.camera = camera;
    }

    public void handle(GameState game, float delta) {
        handleCamera(delta);

        // Клетка под курсором — через камеру: мышь живёт в пикселях окна, а клетка —
        // в мире, и только камера знает, куда сейчас смотрит окно и с каким зумом.
        game.setHover(camera.pickTile(Gdx.input.getX(), Gdx.input.getY(), game.world()));

        // Выбор инструмента: идём по СПИСКУ инструментов, а не по руками написанной
        // лесенке «if (нажата 1) … if (нажата 5)». Номер слота объявлен в самом Tool,
        // и забыть его нельзя: не скомпилируется.
        for (Tool tool : Tool.values()) {
            if (Gdx.input.isKeyJustPressed(keyForSlot(tool.hotkeySlot()))) {
                game.selectTool(tool);
            }
        }

        // Открыть технологию: F1..F4 по списку Tech — снова НЕ лесенка из if'ов, а цикл
        // по данным. Добавится пятая технология — клавиша появится сама.
        for (Tech tech : Tech.values()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1 + tech.ordinal())) {
                game.research().research(tech); // сам проверит, можно ли: очки, предпосылки
            }
        }

        // Поворот и пауза.
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            game.rotateDirection();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            game.togglePause();
        }

        // Отмена/повтор. Ctrl+Z / Ctrl+Y — стандарт, к которому привыкла рука.
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            game.undo();
        }
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            game.redo();
        }

        // Сохранение (F5) и загрузка (F9). Загрузка населяет ТЕКУЩИЙ мир заново — камера и
        // рендер продолжают смотреть на тот же объект, пересобирать их не нужно.
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            trySave(game);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            tryLoad(game);
        }

        // Строительство/снос мышью — теперь через команды. ЛКМ/ПКМ можно ДЕРЖАТЬ и вести
        // линию: весь жест копится в один штрих и ложится в историю одной отменой.
        placeStroke.update(Gdx.input.isButtonPressed(Input.Buttons.LEFT), game);
        removeStroke.update(Gdx.input.isButtonPressed(Input.Buttons.RIGHT), game);
    }

    /**
     * Один непрерывный жест мыши (зажал → вёл → отпустил) как накопитель команд.
     *
     * <p><b>Зачем отдельная сущность.</b> Пока кнопка зажата, каждую новую клетку под
     * курсором надо применить СРАЗУ (иначе линия не рисуется), но в историю весь жест
     * обязан лечь ОДНОЙ отменой. Штрих и решает это противоречие: применяет команды на
     * лету, копит их у себя, а на отпускании кнопки коммитит разом (одну — как есть,
     * несколько — как {@link CompositeAction}).
     *
     * <p>{@code touched} гасит дребезг: пока держишь кнопку на одной клетке, команда для
     * неё создаётся лишь однажды, а не каждый кадр.
     */
    private static final class Stroke {

        private final boolean place; // true — постройка, false — снос
        private final List<PlayerAction> actions = new ArrayList<>();
        private final Set<Long> touched = new HashSet<>();
        private boolean active;

        Stroke(boolean place) {
            this.place = place;
        }

        void update(boolean pressed, GameState game) {
            if (pressed) {
                active = true;
                game.hover().ifPresent(cell -> step(cell, game));
            } else if (active) {
                commit(game); // кнопку отпустили — закрываем жест
            }
        }

        private void step(Cell cell, GameState game) {
            long key = ((long) cell.x() << 32) | (cell.y() & 0xFFFFFFFFL);
            if (!touched.add(key)) {
                return; // эту клетку в текущем жесте уже трогали
            }
            PlayerAction action = place
                    ? new PlaceBuilding(cell, Building.create(game.tool(), game.direction()))
                    : new RemoveBuilding(cell);
            action.apply(game.world());        // применяем сразу — игрок видит результат
            if (action.hadEffect()) {
                actions.add(action);           // пустые (guard отклонил) в историю не кладём
            }
        }

        private void commit(GameState game) {
            active = false;
            if (!actions.isEmpty()) {
                game.commit(actions.size() == 1
                        ? actions.get(0)
                        : new CompositeAction(actions));
            }
            actions.clear();
            touched.clear();
        }
    }

    private void trySave(GameState game) {
        try {
            saveService.save(game, SAVE_PATH);
            System.out.println("Сохранено: " + SAVE_PATH.toAbsolutePath());
        } catch (IOException e) {
            // Слой ввода — часть движка, ему можно писать в консоль. Ронять игру из-за
            // неудачной записи файла нельзя: игрок просто увидит сообщение и продолжит.
            System.err.println("Не удалось сохранить: " + e.getMessage());
        }
    }

    private void tryLoad(GameState game) {
        try {
            loadService.restore(game, loadService.read(SAVE_PATH));
            System.out.println("Загружено: " + SAVE_PATH.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Не удалось загрузить: " + e.getMessage());
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
     * которому запрещено знать про движок (это стережёт {@code ArchitectureTest}).
     * Слой ввода про движок знать обязан — вот пусть он и переводит.
     */
    private static int keyForSlot(int slot) {
        return Input.Keys.NUM_0 + slot;
    }
}
