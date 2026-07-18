package com.rustorio.game;

import com.rustorio.core.Balance;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.ProductionBus;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tint;
import com.rustorio.core.Tool;
import com.rustorio.game.action.PlayerAction;
import com.rustorio.game.view.Corner;
import com.rustorio.game.view.HudPanel;
import com.rustorio.game.view.Overlay;
import com.rustorio.game.view.PanelRow;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import com.rustorio.sim.Simulation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Всё состояние игры в одном месте + продвижение времени.
 *
 * <p>{@code GameState} — «мешок состояния», живущий между кадрами: мир,
 * выбранный инструмент/направление, пауза, клетка под курсором. Ввод и
 * отрисовка работают с этим объектом через методы, а не лезут в чужие поля
 * напрямую (инкапсуляция вместо «публичных полей», как было в Rust-версии).
 *
 * <p>Единственная логика здесь — {@link #update(float)}: приём «фиксированный
 * тик + аккумулятор» (Gaffer «Fix Your Timestep»). Симуляция идёт строго по
 * {@link Config#TICK}, а кадры рисуются с любой частотой — мир ведёт себя
 * одинаково на быстром и медленном железе.
 */
public final class GameState {

    private final World world;
    /** Симуляция — объект, а не статические методы: она владеет буферами (задача B2). */
    private final Simulation simulation;
    /**
     * Изменяемый баланс игры. Живёт здесь, а не в {@link Config}: апгрейды обязаны
     * менять числа во время игры, а константы времени компиляции менять нельзя.
     */
    private final Balance balance = new Balance();
    /** Прогресс исследований: очки из лабораторий и открытые технологии. */
    private final Research research = new Research(balance);
    /** Шина событий производства (шаблон Observer): машины публикуют, наблюдатели слушают. */
    private final ProductionBus production = new ProductionBus();
    /** Один из наблюдателей: суммарное производство по предметам. */
    private final ProductionStats stats = new ProductionStats();
    /** Слой поверх мира (панели, подсветки, уведомления). Наполняет игра, рисует render. */
    private final Overlay overlay = new Overlay();
    /** История отмен строительных действий (паттерн Command). */
    private final ActionHistory history = new ActionHistory();
    private Tool tool = Tool.MINER;
    private Direction direction = Direction.EAST;
    private boolean paused = false;
    /** Первый угол выделяемой области чертежа (второй — клетка под курсором). */
    private @Nullable Cell blueprintCorner = null;
    /** Снятый чертёж «в руке», готовый к штамповке. */
    private @Nullable Blueprint heldBlueprint = null;
    /** Клетка под курсором в этом кадре (её ставит ввод, читает отрисовка). */
    private @Nullable Cell hover = null;
    /** Накопленное реальное время, ещё не «проигранное» в тиках. */
    private float accumulator = 0f;

    public GameState(World world) {
        this.world = world;
        this.simulation = new Simulation(world);
        // Подписываем наблюдателей ОДИН раз. Машины про них не знают — публикуют в шину.
        production.subscribe(stats);
        production.subscribe(new ProductionLog());
    }

    /**
     * Продвинуть симуляцию на прошедшее время кадра.
     *
     * @param deltaTime секунд с прошлого кадра (его даёт слой libGDX —
     *                  {@code Gdx.graphics.getDeltaTime()}). Передаём как
     *                  аргумент, а не берём из глобального движка, чтобы логику
     *                  можно было тестировать без окна.
     */
    public void update(float deltaTime) {
        if (paused) {
            return;
        }
        // Ограничиваем «наигранное» время сверху: после долгого зависания не
        // пытаемся отработать десятки тиков разом (иначе — «спираль смерти»).
        accumulator = Math.min(accumulator + deltaTime, Config.MAX_FRAME_TIME);
        // Контекст создаётся ОДИН раз за тик, а не на каждое здание: все его поля
        // одинаковы для всех зданий в пределах шага.
        TickContext ctx = new TickContext(Config.TICK, balance, production);
        while (accumulator >= Config.TICK) {
            accumulator -= Config.TICK;
            simulation.step(ctx);
            // Забираем очки из лабораторий сразу после шага мира: они уже начислены.
            research.collect(world);
        }
    }

    // ── Состояние: чтение ────────────────────────────────────────────
    public World world() {
        return world;
    }

    /** Баланс игры (его читает отрисовка, его же меняют апгрейды). */
    public Balance balance() {
        return balance;
    }

    /** Прогресс исследований (его читает интерфейс, в нём же открывают технологии). */
    public Research research() {
        return research;
    }

    /** Суммарное производство по предметам (читает интерфейс/отладка). */
    public ProductionStats stats() {
        return stats;
    }

    /** Слой поверх мира — его читает render, наполняет {@link #prepareFrame}. */
    public Overlay overlay() {
        return overlay;
    }

    /**
     * Собрать слой поверх мира на этот кадр: состарить уведомления и заново наполнить панели и
     * подсветки. Зовётся экраном КАЖДЫЙ кадр, между вводом и отрисовкой.
     *
     * <p>Это и есть точка, куда будущий урок добавит свою визуализацию: заполнить {@link
     * #overlay} — и оно появится на экране без единой правки в {@code render}. Сейчас здесь два
     * живых примера каналов: панель производства (данные из наблюдателя D2) и подсветка клетки,
     * куда уйдёт продукт выбранного здания.
     */
    public void prepareFrame(float delta) {
        overlay.age(delta);
        overlay.clearFrame();

        // Канал «панель» с иконками: сводка производства (данные наблюдателя D2).
        List<PanelRow> rows = new ArrayList<>();
        for (Map.Entry<Item, Long> entry : stats.snapshot().entrySet()) {
            rows.add(PanelRow.of(entry.getKey(), String.valueOf(entry.getValue())));
        }
        if (rows.isEmpty()) {
            rows.add(PanelRow.text("none"));
        }
        // Текст UI — латиницей: встроенный шрифт движка кириллицу не рисует (см. Renderer).
        overlay.panel(new HudPanel("Production", rows, Corner.TOP_RIGHT));

        // Превью чертежа подсветкой (канал overlay): выделяемая область или будущий оттиск.
        hover().ifPresent(cell -> {
            if (blueprintCorner != null) {
                highlightRect(blueprintCorner, cell, Tint.RANGE);   // что попадёт в чертёж
            } else if (heldBlueprint != null) {
                for (Cell c : heldBlueprint.cells(cell)) {
                    overlay.highlight(c.x(), c.y(), Tint.GHOST);    // куда ляжет оттиск
                }
            }
        });
    }

    private void highlightRect(Cell a, Cell b, Tint tint) {
        int minX = Math.min(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int maxX = Math.max(a.x(), b.x());
        int maxY = Math.max(a.y(), b.y());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                overlay.highlight(x, y, tint);
            }
        }
    }

    public Tool tool() {
        return tool;
    }

    public Direction direction() {
        return direction;
    }

    public boolean isPaused() {
        return paused;
    }

    public Optional<Cell> hover() {
        return Optional.ofNullable(hover);
    }

    /**
     * Доля прожитого тика (0..1) — нужна ТОЛЬКО отрисовке.
     *
     * <p>Симуляция шагает раз в {@link Config#TICK} (пять с половиной раз в
     * секунду), а кадров рисуется шестьдесят. Без этого числа предмет на ленте
     * дёргался бы скачками; с ним рендер показывает его между прошлой и текущей
     * позицией — и движение становится плавным, хотя мир по-прежнему думает
     * целыми тиками.
     */
    public float tickAlpha() {
        return Math.min(accumulator / Config.TICK, 1f);
    }

    /** Сколько отмен доступно сейчас (читает HUD). */
    public int undoDepth() {
        return history.undoDepth();
    }

    // ── Действия игрока (паттерн Command) ─────────────────────────────

    /**
     * Выполнить команду и записать её в историю (атомарно: применили → запомнили).
     * Канонический «вызов» команды.
     */
    public void perform(PlayerAction action) {
        action.apply(world);
        history.push(action);
    }

    /**
     * Записать в историю команду, которую слой ввода уже применил САМ.
     *
     * <p>Нужно протаскиванию: пока игрок ведёт линию с зажатой ЛКМ, каждая клетка
     * применяется сразу (иначе линия не рисовалась бы под курсором), а в историю весь
     * штрих кладётся ОДНОЙ командой лишь на отпускании кнопки. Здесь мы только запоминаем,
     * не применяя повторно.
     */
    public void commit(PlayerAction action) {
        history.push(action);
    }

    /** Забыть историю отмен. Загрузка сохранения зовёт это: прежние команды больше не к месту. */
    public void resetHistory() {
        history.clear();
    }

    /** Отменить последнее строительное действие. */
    public void undo() {
        history.undo(world);
    }

    /** Повторить последнее отменённое действие. */
    public void redo() {
        history.redo(world);
    }

    // ── Чертёж (расширение Command, урок D3) ──────────────────────────

    /**
     * Клавиша чертежа: первое нажатие отмечает угол области, второе — снимает чертёж с
     * прямоугольника «угол → клетка под курсором».
     */
    public void blueprintKey() {
        hover().ifPresent(cell -> {
            if (blueprintCorner == null) {
                blueprintCorner = cell;
            } else {
                heldBlueprint = Blueprint.capture(world, blueprintCorner, cell);
                blueprintCorner = null;
            }
        });
    }

    /** Штамповать снятый чертёж от клетки под курсором — одной отменяемой командой. */
    public void stampBlueprint() {
        if (heldBlueprint == null || heldBlueprint.isEmpty()) {
            return;
        }
        hover().ifPresent(origin -> perform(heldBlueprint.stampAt(origin)));
    }

    /** Сбросить незавершённое выделение и чертёж «в руке». */
    public void clearBlueprint() {
        blueprintCorner = null;
        heldBlueprint = null;
    }

    // ── Состояние: изменение (этим пользуется слой ввода) ─────────────
    public void selectTool(Tool tool) {
        this.tool = tool;
    }

    public void rotateDirection() {
        this.direction = direction.rotateCw();
    }

    public void togglePause() {
        this.paused = !paused;
    }

    public void setHover(@Nullable Cell hover) {
        this.hover = hover;
    }
}
