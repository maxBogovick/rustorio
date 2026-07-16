package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Бур: стоит на руде, раз в {@link Config#MINER_TIME} кладёт руду в выход и отдаёт
 * её соседу по направлению {@code dir}.
 *
 * <p><b>Про {@code onOre}.</b> «Есть ли подо мной руда» — свойство КЛЕТКИ, а бур
 * своих координат не знает. Поэтому мир сообщает буру факт ОДИН раз, при постройке
 * ({@link World#place} знает клетку), а бур его кэширует. Это безопасно, пока руда
 * не иссякает. <b>Если появится истощение руды — мир ОБЯЗАН сообщить буру об
 * изменении</b> через {@code setOnOre}, иначе бур продолжит «копать» из пустоты.
 */
public final class Miner implements Building {

    private final Direction dir;
    private boolean onOre;
    private float cooldown;
    /** Готовая руда на выходе (или {@code null} — выход пуст). */
    private @Nullable Item output;

    public Miner(Direction dir) {
        this.dir = dir;
        this.cooldown = Config.MINER_TIME;
    }

    /** Мир сообщает буру, стоит ли он на руде (см. javadoc класса). */
    void setOnOre(boolean onOre) {
        this.onOre = onOre;
    }

    @Override
    public void update(TickContext ctx) {
        if (output != null || !onOre) {
            return; // выход занят или копать нечего
        }
        cooldown -= ctx.dt();
        if (cooldown <= 0f) {
            output = Item.IRON_ORE;
            cooldown = Config.MINER_TIME;
        }
    }

    @Override
    public Optional<Handoff> output() {
        return output == null ? Optional.empty() : Optional.of(new Handoff(output, dir));
    }

    @Override
    public boolean canAccept(Item item) {
        return false; // бур ничего не принимает — он источник
    }

    @Override
    public void accept(Item item) {
        // Ничего: буру нельзя ничего отдать.
    }

    @Override
    public void removeOutput() {
        output = null;
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    // ── Чтение состояния для отрисовки ────────────────────────────────
    public Direction dir() {
        return dir;
    }

    public boolean onOre() {
        return onOre;
    }

    /** Доля выполненной добычи 0..1 — по ней рендер выбирает кадр анимации бура. */
    public float progressFraction() {
        return Math.clamp(1f - cooldown / Config.MINER_TIME, 0f, 1f);
    }

    /** Руда на выходе, если есть. */
    public Optional<Item> outputItem() {
        return Optional.ofNullable(output);
    }
}
