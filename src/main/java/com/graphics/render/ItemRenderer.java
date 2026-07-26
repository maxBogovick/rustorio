package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Item layer: the cargo a building is holding "in transit" ({@code Building.heldItem()}) — on a
 * belt, in a miner's hands, in a splitter, underground. A small colored circle is drawn above the
 * building's own sprite every tick, directly reflecting {@code heldItem()}: the moment cargo
 * leaves a miner's hands, the circle disappears in the same tick.
 *
 * <p>A circle with a dark outline rather than a real item texture: the placeholder art in {@code
 * resources/} is 3x4/4x4-pixel and indistinguishable at any scale between a chain's ore, plate and
 * gear. Until real art exists, a solid color per item (see {@link Palette#itemColor}) reads as an
 * intentional token; a flat colored square would read as an unfinished placeholder instead.
 */
final class ItemRenderer {

    /** Диаметр кружка меньше клетки — чтобы отличаться от здания под ним, а не перекрывать его. */
    private static final float DIAMETER_SCALE = 0.46f;
    /** Тёмная обводка — то, что превращает плоское пятно в узнаваемый «предмет» с краем. */
    private static final Color OUTLINE = new Color(0f, 0f, 0f, 0.55f);

    /** One piece of visible cargo, already in screen coordinates — collected once, drawn twice. */
    private record Cargo(float x, float y, Item item) {
    }

    private final ShapeRenderer shapes;
    private final Grid grid;

    ItemRenderer(ShapeRenderer shapes, Grid grid) {
        this.shapes = shapes;
        this.grid = grid;
    }

    /**
     * Only visits buildings within {@code visible} (P4-04, BUG_FIX_PROGRESS.md) instead of the
     * whole map, and collects their cargo into {@link #cargoIn} ONCE — the fill pass and the
     * outline pass (separate {@code ShapeRenderer.ShapeType}s, can't be mixed into one {@code
     * begin}/{@code end}) read the same list instead of each re-walking the world.
     */
    void render(World world, TileRange visible) {
        float radius = GfxConfig.TILE * DIAMETER_SCALE / 2f;
        List<Cargo> cargo = cargoIn(world, visible);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Cargo c : cargo) {
            shapes.setColor(Palette.itemColor(c.item()));
            shapes.circle(c.x(), c.y(), radius, 20);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(OUTLINE);
        for (Cargo c : cargo) {
            shapes.circle(c.x(), c.y(), radius, 20);
        }
        shapes.end();
    }

    private List<Cargo> cargoIn(World world, TileRange visible) {
        float half = GfxConfig.TILE / 2f;
        List<Cargo> cargo = new ArrayList<>();
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(),
                (x, y, building) -> building.heldItem().ifPresent(held ->
                        cargo.add(new Cargo(grid.x(x) + half, grid.yBottom(y) + half, held))));
        return cargo;
    }
}
