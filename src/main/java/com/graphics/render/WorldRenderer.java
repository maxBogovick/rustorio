package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.Item;
import com.rustorio.domain.OreLayout;

/**
 * Ground layer: dirt, ore patches and grid lines. Only walks the visible cells from {@link
 * TileRange}.
 *
 * <p>Which ore (if any) lies under a cell comes from the same {@link OreLayout} the world builds
 * miners against — injected here rather than read from a static table, so this layer stays in
 * sync with whatever layout a given {@code World} was actually configured with. Iron and bronze
 * are colored differently ({@link #oreColor}) so a player can tell them apart before ever
 * building a miner, instead of checking the HUD after the fact.
 */
final class WorldRenderer {

    private final ShapeRenderer shapes;
    private final Grid grid;
    private final OreLayout oreLayout;

    WorldRenderer(ShapeRenderer shapes, Grid grid, OreLayout oreLayout) {
        this.shapes = shapes;
        this.grid = grid;
        this.oreLayout = oreLayout;
    }

    void render(TileRange range) {
        float tile = GfxConfig.TILE;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                shapes.setColor(oreLayout.oreAt(x, y).map(WorldRenderer::oreColor).orElse(Palette.GROUND));
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

    /** Cell color for an ore of this kind — a new ore gets its color here, in one line. */
    private static Color oreColor(Item ore) {
        return ore == Item.BRONZE_ORE ? Palette.ORE_BRONZE : Palette.ORE;
    }
}
