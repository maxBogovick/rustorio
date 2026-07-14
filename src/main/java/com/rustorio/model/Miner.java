package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Бур: стоит на руде, раз в {@link Config#MINER_TIME} кладёт руду в выход,
 * затем отдаёт её соседу по направлению {@code dir}.
 *
 * <p>Поля приватные (инкапсуляция), наружу торчат только осмысленные геттеры —
 * их читает отрисовка. {@code dir} неизменяемо ({@code final}): направление
 * задаётся при постройке и не меняется.
 */
public final class Miner implements Building {

    private final Direction dir;
    private float cooldown;
    /** Готовая руда на выходе (или {@code null} — выход пуст). */
    private @Nullable Item output;

    public Miner(Direction dir) {
        this.dir = dir;
        this.cooldown = Config.MINER_TIME;
        this.output = null;
    }

    @Override
    public void update(float dt, boolean hasOre) {
        // Бур копает, только пока выход свободен и под ним есть руда.
        if (output == null && hasOre) {
            cooldown -= dt;
            if (cooldown <= 0f) {
                output = Item.IRON_ORE;
                cooldown = Config.MINER_TIME;
            }
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

    // ── Геттеры для отрисовки (только чтение состояния) ───────────────
    public Direction dir() {
        return dir;
    }

    /** Оставшийся откат добычи: {@link Config#MINER_TIME} → 0. */
    public float cooldown() {
        return cooldown;
    }

    /** Руда на выходе, если есть. */
    public Optional<Item> outputItem() {
        return Optional.ofNullable(output);
    }
}
