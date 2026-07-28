package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.Item;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.Terrain;
import java.util.Optional;

/**
 * Ground layer: dirt, ore patches, terrain obstacles and grid lines. Only walks the visible cells
 * from {@link TileRange}.
 *
 * <p>Which ore (if any) lies under a cell comes from the same {@link OreLayout} the world builds
 * miners against — injected here rather than read from a static table, so this layer stays in
 * sync with whatever layout a given {@code World} was actually configured with. Iron and bronze
 * are colored differently ({@link #oreColor}) so a player can tell them apart before ever
 * building a miner, instead of checking the HUD after the fact.
 *
 * <p><b>Owner decision (X-02, DEV_TASKS.md):</b> terrain is checked and colored before ore — the
 * two never actually overlap ({@link OreLayout#terrainAt}'s implementations guarantee ore always
 * wins any accidental overlap at generation time), so the order here is only a tie-break that in
 * practice never triggers, not a real precedence decision.
 *
 * <p><b>Owner decision (D-05, DEV_TASKS.md):</b> coal gets BOTH a flat color ({@link
 * Palette#ORE_COAL}, same treatment as iron/bronze) AND {@code resources/coal_ore.png} drawn on
 * top, in a separate {@link SpriteBatch} pass — the card's acceptance criterion names the file
 * specifically ("resources/coal_ore.png используется в рендере"), and this is the one ground
 * texture actually packed into the atlas; iron/bronze stay color-only (their sprites are
 * deliberately unused placeholders — see {@link Textures}).
 */
final class WorldRenderer {

    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final Grid grid;
    private final OreLayout oreLayout;
    private final Textures textures;

    WorldRenderer(ShapeRenderer shapes, SpriteBatch batch, Grid grid, OreLayout oreLayout, Textures textures) {
        this.shapes = shapes;
        this.batch = batch;
        this.grid = grid;
        this.oreLayout = oreLayout;
        this.textures = textures;
    }

    void render(TileRange range) {
        float tile = GfxConfig.TILE;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                shapes.setColor(cellColor(x, y));
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

        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                if (oreLayout.oreAt(x, y).equals(Optional.of(Item.COAL))) {
                    batch.draw(textures.coalOre(), grid.x(x), grid.yBottom(y), tile, tile);
                }
            }
        }
        batch.end();
    }

    /** Terrain first, then ore, then plain ground — see the class javadoc for why the order never actually matters. */
    private Color cellColor(int x, int y) {
        Terrain terrain = oreLayout.terrainAt(x, y);
        if (terrain != Terrain.GROUND) {
            return terrainColor(terrain);
        }
        return oreLayout.oreAt(x, y).map(WorldRenderer::oreColor).orElse(Palette.GROUND);
    }

    private static Color terrainColor(Terrain terrain) {
        return terrain == Terrain.WATER ? Palette.TERRAIN_WATER : Palette.TERRAIN_ROCK;
    }

    /** Cell color for an ore of this kind — a new ore gets its color here, in one line. */
    private static Color oreColor(Item ore) {
        return switch (ore) {
            case BRONZE_ORE -> Palette.ORE_BRONZE;
            case COAL -> Palette.ORE_COAL;
            default -> Palette.ORE; // IRON_ORE — the only other kind oreAt() ever actually returns
        };
    }
}
