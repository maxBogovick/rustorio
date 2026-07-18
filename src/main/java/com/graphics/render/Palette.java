package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.rustorio.core.Tint;

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

    /** Смысловой цвет подсветки → полупрозрачная заливка клетки. */
    static Color tint(Tint tint) {
        return switch (tint) {
            case NEUTRAL -> T_NEUTRAL;
            case GOOD -> T_GOOD;
            case WARN -> T_WARN;
            case BAD -> T_BAD;
            case SELECT -> T_SELECT;
            case RANGE -> T_RANGE;
            case GHOST -> T_GHOST;
        };
    }

    /** Тот же смысл, но насыщенным цветом — для линий и текста. */
    static Color tintStrong(Tint tint) {
        return switch (tint) {
            case NEUTRAL -> HINT;
            case GOOD -> WORKING;
            case WARN -> BAR;
            case BAD -> IDLE;
            case SELECT -> Color.WHITE;
            case RANGE -> TS_RANGE;
            case GHOST -> GHOST;
        };
    }

    private Palette() {
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }
}
