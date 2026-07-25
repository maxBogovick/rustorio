package com.rustorio.domain;

/** One of the four grid directions, carrying its own unit offset. */
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

    /** Next direction clockwise (declaration order doubles as rotation order). */
    public Direction rotate() {
        Direction[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
