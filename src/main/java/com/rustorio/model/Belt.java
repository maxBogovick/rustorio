package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Лента: держит ровно один предмет и толкает его вперёд по направлению
 * {@code dir}. Сама ничего не производит — только переносит.
 */
public final class Belt implements Building {

    private final Direction dir;
    /** Предмет на ленте (или {@code null} — лента пуста). */
    private @Nullable Item item;

    public Belt(Direction dir) {
        this.dir = dir;
        this.item = null;
    }

    @Override
    public void update(float dt, boolean hasOre) {
        // Лента за тик не «работает» сама — предметы двигает система move_items.
    }

    @Override
    public Optional<Handoff> output() {
        return item == null ? Optional.empty() : Optional.of(new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item incoming) {
        return item == null; // свободна ⇒ примет любой предмет
    }

    @Override
    public void accept(Item incoming) {
        this.item = incoming;
    }

    @Override
    public void removeOutput() {
        this.item = null;
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    // ── Геттеры для отрисовки ────────────────────────────────────────
    public Direction dir() {
        return dir;
    }

    public Optional<Item> item() {
        return Optional.ofNullable(item);
    }
}
