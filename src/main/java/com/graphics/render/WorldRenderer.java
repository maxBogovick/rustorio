package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.Item;
import com.rustorio.OreMap;

/**
 * Слой «земля»: грунт, рудные области и линии сетки. Обходит только видимые клетки из
 * {@link TileRange}.
 *
 * <p>Где лежит руда И КАКАЯ, знает {@link OreMap#oreAt} (функция от координат — та же карта, что
 * была, только теперь возвращает сорт, а не просто «есть/нет»). Железо и бронза раскрашены
 * по-разному ({@link #oreColor}), чтобы отличить их можно было ещё ДО постройки бура — не
 * тыкать наугад и не проверять по HUD задним числом, что накопал.
 */
final class WorldRenderer {

    private final ShapeRenderer shapes;
    private final Grid grid;

    WorldRenderer(ShapeRenderer shapes, Grid grid) {
        this.shapes = shapes;
        this.grid = grid;
    }

    void render(TileRange range) {
        float tile = GfxConfig.TILE;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Item ore = OreMap.oreAt(x, y);
                shapes.setColor(ore == null ? Palette.GROUND : oreColor(ore));
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.GRID);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        }
        shapes.end();
    }

    /** Цвет клетки под руду данного сорта — новая руда получит свой цвет здесь, одной строкой. */
    private static Color oreColor(Item ore) {
        return ore == Item.BRONZE_ORE ? Palette.ORE_BRONZE : Palette.ORE;
    }
}
