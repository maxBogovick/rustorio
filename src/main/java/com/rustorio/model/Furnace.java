package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.List;
import java.util.Map;
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
    private final ProcessKernel kernel;

    public Furnace(Direction dir) {
        this.dir = dir;
        this.kernel = new ProcessKernel(Tool.FURNACE);
    }

    /** Восстановить печь с буфером сырья и очередью готового — для загрузки сохранения (B2). */
    public Furnace(Direction dir, Map<Item, Integer> stock, List<Item> ready) {
        this.dir = dir;
        this.kernel = new ProcessKernel(Tool.FURNACE, stock, ready);
    }

    @Override
    public void update(TickContext ctx) {
        kernel.update(ctx);
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

    // ── Снимок для сохранения (B2) ────────────────────────────────────
    public Map<Item, Integer> stockSnapshot() {
        return kernel.snapshotStock();
    }

    public List<Item> readySnapshot() {
        return kernel.snapshotReady();
    }

    // ── Геттеры для отрисовки ────────────────────────────────────────
    public Direction dir() {
        return dir;
    }

    /** Что показать на машине: готовый продукт, иначе — сырьё со склада. */
    public Optional<Item> displayItem() {
        return kernel.displayItem();
    }

    /** Машина сейчас работает (есть рецепт в работе)? */
    public boolean isWorking() {
        return kernel.progressFraction() > 0f;
    }

    /** Есть ли на складе сырьё (печь по этому решает, гореть ли ей). */
    public boolean hasStock() {
        return kernel.hasStock();
    }

    public float progressFraction() {
        return kernel.progressFraction();
    }
}
