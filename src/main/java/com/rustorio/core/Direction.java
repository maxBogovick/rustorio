package com.rustorio.core;

/**
 * Куда «смотрит» здание: бур/лента/печь отдают предмет в эту сторону.
 *
 * <p>Ось Y растёт ВНИЗ (как в координатах поля), поэтому {@link #NORTH} имеет
 * смещение {@code dy = -1}. Отрисовка сама переводит это в свою систему
 * координат — домен про экран ничего не знает.
 *
 * <p>Смещение хранится прямо в перечислении (каноничная Java-идиома «enum с
 * данными и поведением»), поэтому {@link #dx()}/{@link #dy()} — просто чтение
 * поля, без {@code switch}.
 */
public enum Direction {
    NORTH(0, -1, "N"),
    EAST(1, 0, "E"),
    SOUTH(0, 1, "S"),
    WEST(-1, 0, "W");

    private final int dx;
    private final int dy;
    private final String shortName;

    Direction(int dx, int dy, String shortName) {
        this.dx = dx;
        this.dy = dy;
        this.shortName = shortName;
    }

    /** Смещение по X на соседнюю клетку в этом направлении. */
    public int dx() {
        return dx;
    }

    /** Смещение по Y на соседнюю клетку (ось вниз: North = -1). */
    public int dy() {
        return dy;
    }

    /** Короткое имя для интерфейса (N/E/S/W). */
    public String shortName() {
        return shortName;
    }

    /**
     * Повернуть по часовой стрелке (клавиша R).
     *
     * <p>Порядок объявления N→E→S→W уже совпадает с поворотом по часовой,
     * поэтому берём следующий элемент по кругу — данные задают поведение,
     * дублировать логику в {@code switch} не нужно.
     */
    public Direction rotateCw() {
        Direction[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
