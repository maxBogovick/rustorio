package com.rustorio.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One of the four grid directions, carrying its own unit offset. */
public enum Direction {
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0),
    UP(0, -1);

    private final int dx;
    private final int dy;
    /**
     * Next direction clockwise, precomputed once (P4-02, BUG_FIX_PROGRESS.md) — every enum
     * constant is constructed before any of them can call {@link #values()}, so this can't be set
     * in the constructor; a static block runs after all constants exist and fills it in for every
     * one of them, before any caller can observe a null value. {@code @Nullable} only because
     * NullAway can't see that guarantee across the static block.
     */
    private @Nullable Direction next;

    static {
        Direction[] all = values();
        for (int i = 0; i < all.length; i++) {
            all[i].next = all[(i + 1) % all.length];
        }
    }

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

    /**
     * Next direction clockwise (declaration order doubles as rotation order). Returns the
     * precomputed field instead of calling {@link #values()} — that method clones the enum's
     * backing array on every call, and this is called every tick a splitter routes cargo, plus
     * once per frame per building via {@code secondaryOutputDirection}.
     */
    public Direction rotate() {
        return Objects.requireNonNull(next);
    }
}
