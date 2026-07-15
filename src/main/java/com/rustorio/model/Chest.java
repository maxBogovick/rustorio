package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;

import java.util.Optional;

/**
 * Ящик: просто копит предметы (счётчик). Ничего не отдаёт и не имеет
 * направления — конечная точка любой цепочки.
 */
public final class Chest implements Building {

    private int items;

    public Chest() {
    }

    /**
     * Восстановить ящик с накопленным счётчиком — используется загрузкой сохранения.
     * Отдельный конструктор, а не сеттер: восстановленный объект сразу целостен, и «забыть
     * задать счётчик» невозможно.
     */
    public Chest(int items) {
        this.items = items;
    }

    @Override
    public void update(TickContext ctx) {
        // Ящик пассивен: за тик ничего не делает.
    }

    @Override
    public Optional<Handoff> output() {
        return Optional.empty(); // ящик — «чёрная дыра», наружу не отдаёт
    }

    @Override
    public boolean canAccept(Item item) {
        return true; // примет что угодно
    }

    @Override
    public void accept(Item item) {
        items++;
    }

    @Override
    public void removeOutput() {
        // Нечего убирать: ящик не отдаёт предметы.
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.empty(); // у ящика нет направления
    }

    /** Сколько предметов накоплено (для отрисовки счётчика). */
    public int items() {
        return items;
    }
}
