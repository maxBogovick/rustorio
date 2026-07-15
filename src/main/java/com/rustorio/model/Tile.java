package com.rustorio.model;

/**
 * Одна клетка поля: есть ли под ней руда.
 *
 * <p>Пока это почти пустой «контейнер данных». {@code ore} — {@code final}: залежи
 * задаются при генерации карты и не двигаются. По ходу курса клетка обзаведётся
 * зданием (изменяемое поле — игрок ставит и сносит) и служебными полями симуляции.
 */
public final class Tile {

    private final boolean ore;

    Tile(boolean ore) {
        this.ore = ore;
    }

    /** Есть ли под клеткой залежь руды (бур будет копать только тут). */
    public boolean hasOre() {
        return ore;
    }
}
