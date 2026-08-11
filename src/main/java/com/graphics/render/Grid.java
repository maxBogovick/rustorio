package com.graphics.render;

import com.graphics.GfxConfig;

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
        return gx * GfxConfig.TILE;
    }

    /** Y НИЖНЕГО края клетки-строки {@code gy}. */
    float yBottom(int gy) {
        return (height - 1 - gy) * GfxConfig.TILE;
    }

    /**
     * Screen Y of the bottom edge of a building's whole footprint.
     *
     * <p>The domain stores the anchor at the footprint's top-left in map space and occupies
     * {@code [anchorGy, anchorGy + footprintHeight)} with row growing <em>down</em> the map
     * ({@link com.rustorio.domain.Direction#DOWN}). libGDX draws Y-up, so the southernmost
     * occupied row ({@code anchorGy + footprintHeight - 1}) is the lowest screen edge — not
     * {@link #yBottom(int) yBottom(anchorGy)}. Drawing from the anchor's own bottom and growing
     * upward by {@code footprintHeight} tiles paints the machine one row too high; belts legally
     * placed on the free row above then sit under the sprite and look like they overlap it.
     */
    float yFootprintBottom(int anchorGy, int footprintHeight) {
        return yBottom(anchorGy + footprintHeight - 1);
    }

    /** X центра клетки; {@code gx} может быть дробным (предмет между клетками). */
    float centerX(float gx) {
        return (gx + 0.5f) * GfxConfig.TILE;
    }

    /** Y центра клетки; {@code gy} может быть дробным. */
    float centerY(float gy) {
        return (height - gy - 0.5f) * GfxConfig.TILE;
    }
}
