package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;

/** Все цвета отрисовки в одном месте (перенесены из render.rs Rust-версии). */
final class Palette {

    static final Color BG = rgb(26, 26, 31);
    static final Color GROUND = rgb(38, 41, 46);
    static final Color ORE = rgb(51, 71, 115);
    static final Color GRID = new Color(0, 0, 0, 64 / 255f);
    static final Color HINT = rgb(179, 179, 199);
    static final Color WORKING = Color.GREEN;
    static final Color IDLE = Color.RED;
    static final Color BAR = Color.YELLOW;
    static final Color GHOST = new Color(1, 1, 1, 0.6f);

    private Palette() {
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }
}
