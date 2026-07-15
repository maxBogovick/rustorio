package com.rustorio.game.action;

import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import org.jspecify.annotations.Nullable;

/**
 * Команда «снести здание с клетки». Зеркальна {@link PlaceBuilding}.
 *
 * <p>Запоминает снесённое здание, чтобы откат вернул именно его. Снятая с клетки лента
 * восстанавливается через {@link World#place}, который заново вошьёт её в сеть линий —
 * поэтому откат не рассинхронит транспортные линии.
 *
 * <p><b>Оговорка, честно записанная здесь же.</b> Предметы, ехавшие по снесённой ленте,
 * жили внутри её транспортной линии и при сносе исчезли. Откат вернёт саму ленту, но не
 * её груз: команда откатывает СТРУКТУРУ, а не «время». Для сноса/постройки это ровно то,
 * чего ждёшь; воскрешать груз — задача уровня replay, которого мы сознательно не берём.
 */
public final class RemoveBuilding implements PlayerAction {

    private final int x;
    private final int y;

    /** Что мы сняли — снимок для отката. */
    private @Nullable Building removed;
    private boolean changed;

    public RemoveBuilding(Cell cell) {
        this.x = cell.x();
        this.y = cell.y();
    }

    @Override
    public void apply(World world) {
        if (!world.inBounds(x, y)) {
            changed = false;
            return;
        }
        removed = world.tile(x, y).building();
        world.remove(x, y);
        changed = removed != null; // сносить было что → откат осмыслен
    }

    @Override
    public void revert(World world) {
        if (changed) {
            world.place(x, y, removed); // клетка теперь пуста — постройка пройдёт
        }
    }

    @Override
    public boolean hadEffect() {
        return changed;
    }
}
