package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Слой «поверх мира» (подсветки, линии, HUD-панели, тосты) — КАРКАС. Пока пуст.
 *
 * <p>Раньше рисовал обобщённые данные, которые складывала логика игры. Вернётся, когда логике
 * будет что показывать. Два прохода (мировой и HUD) сохранены — их вызывает {@link Renderer}.
 */
final class OverlayRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;
    private final Grid grid;

    OverlayRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures,
            Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
        this.grid = grid;
    }

    /** Мировой проход — пока пуст. */
    void renderWorld() {
    }

    /** HUD-проход — пока пуст. */
    void renderHud() {
    }
}
