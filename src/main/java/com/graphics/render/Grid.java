package com.graphics.render;

import com.rustorio.core.Config;

/**
 * Перевод «клетка сетки → мировые пиксели» — единственное место Y-flip.
 *
 * <p>Модель считает строки СВЕРХУ (строка 0 — верх карты, как в Rust-версии),
 * libGDX рисует Y-ВВЕРХ. Вся стыковка этих двух миров собрана здесь; ни один
 * под-рендерер не занимается переворотом сам.
 */
final class Grid {

    private final int height;

    Grid(int height) {
        this.height = height;
    }

    /** X левого края клетки-столбца {@code gx}. */
    float x(int gx) {
        return gx * Config.TILE;
    }

    /** Y НИЖНЕГО края клетки-строки {@code gy}. */
    float yBottom(int gy) {
        return (height - 1 - gy) * Config.TILE;
    }

    /** X центра клетки; {@code gx} может быть дробным (предмет между клетками). */
    float centerX(float gx) {
        return (gx + 0.5f) * Config.TILE;
    }

    /** Y центра клетки; {@code gy} может быть дробным. */
    float centerY(float gy) {
        return (height - gy - 0.5f) * Config.TILE;
    }
}
