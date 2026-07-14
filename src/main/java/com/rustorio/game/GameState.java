package com.rustorio.game;

import com.rustorio.core.Balance;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import com.rustorio.sim.Simulation;
import org.jspecify.annotations.Nullable;

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
    private Tool tool = Tool.MINER;
    private Direction direction = Direction.EAST;
    private boolean paused = false;
    /** Клетка под курсором в этом кадре (её ставит ввод, читает отрисовка). */
    private @Nullable Cell hover = null;
    /** Накопленное реальное время, ещё не «проигранное» в тиках. */
    private float accumulator = 0f;

    public GameState(World world) {
        this.world = world;
        this.simulation = new Simulation(world);
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
        TickContext ctx = new TickContext(Config.TICK, balance);
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
