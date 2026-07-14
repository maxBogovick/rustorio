package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;

import java.util.Optional;

/**
 * Печь: принимает руду, за {@link com.rustorio.core.Config#SMELT_TIME} плавит
 * её в пластину и отдаёт соседу по направлению {@code dir}.
 *
 * <p>Вся логика переработки делегирована {@link ProcessKernel} — печь лишь
 * добавляет к нему направление отдачи.
 */
public final class Furnace implements Building {

    private final Direction dir;
    private final ProcessKernel kernel = new ProcessKernel(Tool.FURNACE);

    public Furnace(Direction dir) {
        this.dir = dir;
    }

    @Override
    public void update(float dt, boolean hasOre) {
        kernel.update(dt);
    }

    @Override
    public Optional<Handoff> output() {
        return kernel.output().map(item -> new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item item) {
        return kernel.canAccept(item);
    }

    @Override
    public void accept(Item item) {
        kernel.accept(item);
    }

    @Override
    public void removeOutput() {
        kernel.removeOutput();
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    // ── Геттеры для отрисовки ────────────────────────────────────────
    public Direction dir() {
        return dir;
    }

    public Optional<Item> input() {
        return kernel.input();
    }

    public Optional<Item> outputItem() {
        return kernel.output();
    }

    public float progressFraction() {
        return kernel.progressFraction();
    }
}
