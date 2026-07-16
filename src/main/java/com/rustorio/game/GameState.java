package com.rustorio.game;

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
 * <p>{@code GameState} — «мешок состояния», живущий между кадрами: мир, выбранный
 * инструмент/направление, пауза, клетка под курсором. Ввод и отрисовка работают с
 * этим объектом через методы, а не лезут в чужие поля напрямую (инкапсуляция вместо
 * «публичных полей»).
 *
 * <p>Единственная логика здесь — {@link #update(float)}: приём «фиксированный тик +
 * аккумулятор» (Gaffer «Fix Your Timestep»). Симуляция идёт строго по
 * {@link Config#TICK}, а кадры рисуются с любой частотой — мир ведёт себя одинаково
 * на быстром и медленном железе.
 */
public final class GameState {

    private final World world;
    /** Симуляция — объект, а не статические методы: она владеет буферами. */
    private final Simulation simulation;
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
     *                  {@code Gdx.graphics.getDeltaTime()}). Передаём как аргумент,
     *                  а не берём из глобального движка, чтобы логику можно было
     *                  тестировать без окна.
     */
    public void update(float deltaTime) {
        if (paused) {
            return;
        }
        // Ограничиваем «наигранное» время сверху: после долгого зависания не
        // пытаемся отработать десятки тиков разом (иначе — «спираль смерти»).
        accumulator = Math.min(accumulator + deltaTime, Config.MAX_FRAME_TIME);
        // Контекст создаётся ОДИН раз за вызов: его поля одинаковы для всех тиков кадра.
        TickContext ctx = new TickContext(Config.TICK);
        while (accumulator >= Config.TICK) {
            accumulator -= Config.TICK;
            simulation.step(ctx);
        }
    }

    // ── Состояние: чтение ────────────────────────────────────────────
    public World world() {
        return world;
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
     * <p>Симуляция шагает раз в {@link Config#TICK}, а кадров рисуется шестьдесят.
     * Когда по лентам поедут предметы, без этого числа предмет дёргался бы скачками;
     * с ним рендер показывает его между прошлой и текущей позицией — движение плавное,
     * хотя мир по-прежнему думает целыми тиками.
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
