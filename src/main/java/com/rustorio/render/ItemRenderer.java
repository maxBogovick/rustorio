package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.game.GameState;
import com.rustorio.model.Assembler;
import com.rustorio.model.Belt;
import com.rustorio.model.BeltItemPos;
import com.rustorio.model.BeltSegment;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Lab;
import com.rustorio.model.Miner;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;

/**
 * Слой «предметы»: иконки на машинах, счётчики ящиков/лабораторий и груз,
 * едущий по транспортным линиям.
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
                switch (b) {
                    case Miner m -> m.outputItem().ifPresent(it -> drawItemIcon(px, py, it));
                    // Предметы на лентах рисуются НЕ здесь: они больше не принадлежат
                    // клетке, а едут внутри линии — см. drawBeltItems().
                    case Belt _ -> { }
                    case Furnace f -> f.displayItem().ifPresent(it -> drawItemIcon(px, py, it));
                    case Assembler a -> a.displayItem().ifPresent(it -> drawItemIcon(px, py, it));
                    case Chest c -> drawCounter(px, py, c.items());
                    case Lab lab -> drawCounter(px, py, lab.points());
                    case Splitter _, UndergroundBelt _ -> { /* предметов на них нет */ }
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
