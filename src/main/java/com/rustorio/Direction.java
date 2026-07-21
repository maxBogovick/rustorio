package com.rustorio;

/**
 * Направление на клетчатом поле — куда сдвинуться на одну клетку.
 *
 * <p>Раньше лента понимала только «вправо», зашитое прямо в {@code x + 1} (урок 11, обещание
 * «направление — тема отдельного урока-улучшения потом»). Теперь это отдельное значение: лента
 * не знает про `+1`/`-1` сама, она спрашивает своё направление, куда сдвинуться.
 */
public enum Direction {
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0),
    UP(0, -1);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    /** Следующее направление по часовой стрелке (порядок объявления констант). */
    public Direction rotate() {
        Direction[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
