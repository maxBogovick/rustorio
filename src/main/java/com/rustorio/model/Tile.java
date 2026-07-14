package com.rustorio.model;

/**
 * Одна клетка поля: есть ли под ней руда и какое здание на ней стоит.
 *
 * <p>Простой изменяемый «контейнер данных». {@code ore} — {@code final}: залежи
 * задаются при генерации карты и не двигаются. {@code building} изменяемо:
 * игрок ставит и сносит здания. Наружу — только чтение через геттеры плюс
 * пакетно-приватные операции, которыми пользуется {@link World}.
 */
public final class Tile {

    private final boolean ore;
    private Building building;

    Tile(boolean ore) {
        this.ore = ore;
        this.building = null;
    }

    /** Есть ли под клеткой залежь руды (бур копает только тут). */
    public boolean hasOre() {
        return ore;
    }

    /** Здание на клетке, либо {@code null}, если пусто. */
    public Building building() {
        return building;
    }

    // Пакетно-приватно: менять здание клетки вправе только World,
    // чтобы соблюдались правила постановки/сноса из одного места.
    void setBuilding(Building building) {
        this.building = building;
    }
}
