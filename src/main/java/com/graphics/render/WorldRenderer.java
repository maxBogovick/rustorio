package com.graphics.render;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.OreMap;

/**
 * Слой «земля»: грунт, рудные области и линии сетки. Обходит только видимые клетки из
 * {@link TileRange}.
 *
 * <p>Где лежит руда, знает {@link OreMap} (функция от координат — та же карта, что была).
 * Здания сюда вернутся вместе с остальной логикой; пока рисуется карта и по ней ездит камера.
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
                // Руда — синим, обычный грунт — серым: те же рудные области, что были в игре.
                shapes.setColor(OreMap.hasOre(x, y) ? Palette.ORE : Palette.GROUND);
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
}
