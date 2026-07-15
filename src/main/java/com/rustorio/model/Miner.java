package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Бур: стоит на руде, раз в {@link Config#MINER_TIME} (делённое на множитель скорости)
 * кладёт руду в выход и отдаёт её соседу по направлению {@code dir}.
 *
 * <p><b>Про {@code onOre}.</b> Раньше «есть ли подо мной руда» подавалось буру снаружи,
 * параметром метода {@code update} — и принимать его были вынуждены ВСЕ здания, включая
 * ящик. Корень беды в том, что здание не знает своих координат. Теперь бур узнаёт про
 * руду ОДИН раз, в момент постройки: {@link World#place} знает клетку и сообщает буру.
 *
 * <p><b>Условие, о котором нельзя забыть.</b> Бур КЭШИРУЕТ факт наличия руды. Сегодня
 * руда не иссякает, и это безопасно. Если когда-нибудь появится «истощение руды», мир
 * ОБЯЗАН сообщить буру об изменении — иначе тот продолжит копать из пустоты.
 */
public final class Miner implements Building {

    private final Direction dir;
    private boolean onOre;
    private float cooldown;
    /** Длительность текущего цикла добычи — нужна для полоски прогресса. */
    private float cycleTime = Config.MINER_TIME;
    /** Готовая руда на выходе (или {@code null} — выход пуст). */
    private @Nullable Item output;

    public Miner(Direction dir) {
        this.dir = dir;
        this.cooldown = Config.MINER_TIME;
    }

    /** Восстановить бур с уже готовой рудой на выходе — для загрузки сохранения (B2). */
    public Miner(Direction dir, @Nullable Item output) {
        this(dir);
        this.output = output;
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
        cycleTime = Config.MINER_TIME / ctx.balance().speed(Tool.MINER);
        cooldown -= ctx.dt();
        if (cooldown <= 0f) {
            output = Item.IRON_ORE;
            cooldown = cycleTime;
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
        return Math.clamp(1f - cooldown / cycleTime, 0f, 1f);
    }

    /** Руда на выходе, если есть. */
    public Optional<Item> outputItem() {
        return Optional.ofNullable(output);
    }
}
