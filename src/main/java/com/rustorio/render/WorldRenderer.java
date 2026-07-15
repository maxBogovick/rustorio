package com.rustorio.render;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.core.Config;
import com.rustorio.model.World;

/**
 * Слой «земля»: фон клеток (руда/грунт) и линии сетки.
 * Обходит только клетки из {@link TileRange} — что за кадром, не рисуется вовсе.
 */
final class WorldRenderer {

    private final ShapeRenderer shapes;
    private final Grid grid;

    WorldRenderer(ShapeRenderer shapes, Grid grid) {
        this.shapes = shapes;
        this.grid = grid;
    }

    void render(World world, TileRange range) {
        float tile = Config.TILE;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                shapes.setColor(world.tile(x, y).hasOre() ? Palette.ORE : Palette.GROUND);
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
