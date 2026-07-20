package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** HUD — заголовок и подсказка управления, прибитые к «стеклу» окна. Пока без состояния игры. */
final class HudRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;

    HudRenderer(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;
    }

    void render() {
        float top = Gdx.graphics.getHeight();
        batch.begin();

        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", 20, top - 16);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.9f);
        font.draw(batch, "LMB build miner on ore   WASD pan   wheel zoom", 20, top - 40);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }
}
