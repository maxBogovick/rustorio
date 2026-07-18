package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.core.Appearance;
import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.game.GameState;
import com.rustorio.model.BeltItemPos;
import com.rustorio.model.BeltSegment;
import com.rustorio.model.Building;
import com.rustorio.model.World;

/**
 * Слой «предметы»: иконки на машинах, счётчики ящиков/лабораторий и груз,
 * едущий по транспортным линиям.
 *
 * <p>Как и накладки зданий, иконки и счётчики берутся из {@link Appearance}, который здание
 * рассказывает о себе само, — поэтому графика не знает типов зданий, и новое здание сюда не
 * заглядывает. Груз ЛЕНТ рисуется отдельно ({@link #drawBeltItems}): он принадлежит линии, а
 * не клетке, и ему нужна плавность между тиками.
 */
final class ItemRenderer {

    private static final float TILE = Config.TILE;

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

    void render(World world, GameState game, TileRange range) {
        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                Appearance look = b.appearance();
                // Предметы на лентах — НЕ здесь: они едут внутри линии (см. drawBeltItems).
                if (look.icon() != null) {
                    drawItemIcon(px, py, look.icon());
                }
                if (look.hasCounter()) {
                    drawCounter(px, py, look.counter());
                }
            }
        }
        drawBeltItems(world, game.tickAlpha());
        batch.end();
    }

    /**
     * Предметы, едущие по транспортным линиям.
     *
     * <p><b>Здесь и появляется плавность.</b> Модель считает целыми слотами и
     * шагает пять раз в секунду; {@code alpha} — доля прожитого тика — говорит,
     * насколько предмет уже уехал от прошлой позиции к текущей. Дробные координаты
     * существуют только тут, в отрисовке: симуляция остаётся целочисленной и
     * воспроизводимой.
     */
    private void drawBeltItems(World world, float alpha) {
        float size = TILE * 0.42f;
        for (BeltSegment segment : world.belts().segments()) {
            for (BeltItemPos pos : segment.itemPositions(alpha)) {
                float cx = grid.centerX(pos.x());
                float cy = grid.centerY(pos.y());
                batch.draw(textures.itemTexture(pos.item()),
                        cx - size / 2f, cy - size / 2f, size, size);
            }
        }
    }

    /** Маленькая иконка предмета в нижнем-правом углу клетки. */
    private void drawItemIcon(float px, float py, Item item) {
        float size = TILE * 0.4f;
        batch.draw(textures.itemTexture(item), px + TILE - size - 2, py + 2, size, size);
    }

    /** Число на здании (сколько в ящике, сколько очков в лаборатории). */
    private void drawCounter(float px, float py, int value) {
        font.getData().setScale(0.9f);
        font.setColor(Color.WHITE);
        font.draw(batch, Integer.toString(value), px + 6, py + 20);
    }
}
