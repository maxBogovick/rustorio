package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.BuildingType;
import com.rustorio.Item;
import com.rustorio.ProductionStats;

import static com.graphics.render.BuildingRenderer.hotbar;

/** HUD — заголовок и подсказка управления, прибитые к «стеклу» окна. Пока без состояния игры. */
final class HudRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;

    HudRenderer(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;
    }

    void render(BuildingType selected, ProductionStats stats) {
        float y = Gdx.graphics.getHeight() - 20;   // верхний отступ от края окна
        batch.begin();

        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", 20, y);
        y -= 34;                                   // заголовок крупный — шаг побольше

        font.setColor(Palette.HINT);
        font.getData().setScale(0.9f);
        font.draw(batch, "LMB build miner on ore   WASD pan   wheel zoom", 20, y);
        y -= 22;

        font.draw(batch, hotbar(selected), 20, y);
        y -= 22;

        font.draw(batch, produced(stats), 20, y);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private static String produced(ProductionStats stats) {
        StringBuilder sb = new StringBuilder("Produced:   ");
        for (Item item : Item.values()) {
            sb.append(item.name()).append(' ').append(stats.total(item)).append("    ");
        }
        return sb.toString();
    }
}
