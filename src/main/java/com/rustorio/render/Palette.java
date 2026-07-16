package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;

/**
 * Все цвета отрисовки в одном месте. Новый цвет — новая константа здесь,
 * а не «магический new Color(...)» посреди кода рендера.
 */
final class Palette {

    static final Color BG = rgb(26, 26, 31);
    static final Color GROUND = rgb(38, 41, 46);
    static final Color ORE = rgb(51, 71, 115);
    static final Color GRID = new Color(0, 0, 0, 64 / 255f);
    static final Color HINT = rgb(179, 179, 199);
    static final Color WORKING = Color.GREEN;   // здание работает — зелёная стрелка
    static final Color IDLE = Color.RED;        // простаивает / не на руде — красный
    static final Color BAR = rgb(230, 190, 60);  // полоска прогресса переработки

    private Palette() {
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }
}
