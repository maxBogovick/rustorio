package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Слой «предметы» — КАРКАС. Пока пуст: предметов в логике ещё нет.
 *
 * <p>Ресурсы сохранены, чтобы позже сюда вернулись иконки на зданиях и груз, едущий по лентам.
 */
final class ItemRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final Textures textures;
    private final Grid grid;

    ItemRenderer(SpriteBatch batch, BitmapFont font, Textures textures, Grid grid) {
        this.batch = batch;
        this.font = font;
        this.textures = textures;
        this.grid = grid;
    }

    /** Пока ничего не рисует — предметы вернутся сюда позже. */
    void render() {

    }
}
