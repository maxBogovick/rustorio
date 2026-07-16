package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.model.Belt;
import com.rustorio.model.BeltItemPos;
import com.rustorio.model.BeltSegment;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Miner;
import com.rustorio.model.World;

/**
 * Слой «предметы»: иконки на машинах, счётчики ящиков и груз, едущий по
 * транспортным линиям.
 *
 * <p>Отдельный слой от {@link BuildingRenderer}, потому что предметы рисуются ПОВЕРХ
 * спрайтов зданий и накладок: сначала здание, затем груз/счётчик на нём.
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

    void render(World world, TileRange range, float tickAlpha) {
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
                    case Furnace f -> f.outputItem().ifPresent(it -> drawItemIcon(px, py, it));
                    // Предметы на лентах рисуются НЕ здесь: они больше не принадлежат
                    // клетке, а едут внутри линии — см. drawBeltItems().
                    case Belt _ -> { }
                    case Chest c -> drawCounter(px, py, c.items());
                }
            }
        }
        drawBeltItems(world, tickAlpha);
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
        for (BeltSegment segment : world.belts().segments()) {
            for (BeltItemPos pos : segment.itemPositions(alpha)) {
                float cx = grid.centerX(pos.x());
                float cy = grid.centerY(pos.y());
                drawItemCentered(cx - TILE / 2f, cy - TILE / 2f, pos.item());
            }
        }
    }

    /** Иконка предмета в правом нижнем углу клетки. */
    private void drawItemIcon(float px, float py, Item item) {
        float size = TILE * 0.4f;
        batch.draw(iconFor(item), px + TILE - size - 2, py + 2, size, size);
    }

    /** Иконка предмета по центру клетки (груз на ленте). */
    private void drawItemCentered(float px, float py, Item item) {
        float size = TILE * 0.5f;
        batch.draw(iconFor(item), px + (TILE - size) / 2f, py + (TILE - size) / 2f, size, size);
    }

    /** Число на здании (сколько накоплено в ящике). */
    private void drawCounter(float px, float py, int value) {
        font.getData().setScale(0.9f);
        font.setColor(Color.WHITE);
        font.draw(batch, Integer.toString(value), px + 6, py + 20);
    }

    /** Спрайт предмета. Исчерпывающий switch по {@code enum Item}. */
    private TextureRegion iconFor(Item item) {
        return switch (item) {
            case IRON_ORE -> textures.ironOre;
            case IRON_PLATE -> textures.ironPlate;
        };
    }
}
