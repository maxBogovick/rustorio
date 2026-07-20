package com.graphics.render;

import com.badlogic.gdx.graphics.Color;

/** Все цвета отрисовки в одном месте (перенесены из render.rs Rust-версии). */
final class Palette {

    static final Color BG = rgb(26, 26, 31);
    static final Color GROUND = rgb(42, 46, 54);
    static final Color ORE = rgb(51, 71, 115);   // рудные области — заметный синий
    static final Color GRID = rgb(90, 96, 110);
    static final Color HINT = rgb(179, 179, 199);
    static final Color WORKING = Color.GREEN;
    static final Color IDLE = Color.RED;
    static final Color BAR = Color.YELLOW;
    static final Color GHOST = new Color(1, 1, 1, 0.6f);

    // Полупрозрачные заливки для подсветок клеток (см. Tint и OverlayRenderer).
    static final Color T_NEUTRAL = new Color(0.70f, 0.70f, 0.78f, 0.30f);
    static final Color T_GOOD = new Color(0.30f, 0.90f, 0.40f, 0.35f);
    static final Color T_WARN = new Color(1.00f, 0.80f, 0.20f, 0.35f);
    static final Color T_BAD = new Color(1.00f, 0.30f, 0.30f, 0.35f);
    static final Color T_SELECT = new Color(1.00f, 1.00f, 1.00f, 0.30f);
    static final Color T_RANGE = new Color(0.30f, 0.60f, 1.00f, 0.25f);
    static final Color T_GHOST = new Color(1.00f, 1.00f, 1.00f, 0.20f);

    // Насыщенные цвета для линий и текста (заливки полупрозрачны, а тут нужна читаемость).
    static final Color TS_RANGE = new Color(0.40f, 0.70f, 1.00f, 1f);

    // Методы tint()/tintStrong() (смысловой цвет подсветки → заливка) убраны вместе с доменом:
    // они переводили com.rustorio.core.Tint. Вернутся, когда вернутся наложения (OverlayRenderer).
    // Сами цвета оставлены — пригодятся.

    private Palette() {
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }
}
