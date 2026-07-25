package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.world.World;

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

    private final ShapeRenderer shapes;
    private final Grid grid;

    ItemRenderer(ShapeRenderer shapes, Grid grid) {
        this.shapes = shapes;
        this.grid = grid;
    }

    void render(World world) {
        float radius = GfxConfig.TILE * DIAMETER_SCALE / 2f;
        float half = GfxConfig.TILE / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        world.forEachBuilding((x, y, building) -> building.heldItem().ifPresent(held -> {
            shapes.setColor(Palette.itemColor(held));
            shapes.circle(grid.x(x) + half, grid.yBottom(y) + half, radius, 20);
        }));
        shapes.end();

        // Outline as a second pass: ShapeRenderer draws one ShapeType per begin/end — fill and
        // line can't be mixed into a single call.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(OUTLINE);
        world.forEachBuilding((x, y, building) -> building.heldItem().ifPresent(held ->
                shapes.circle(grid.x(x) + half, grid.yBottom(y) + half, radius, 20)));
        shapes.end();
    }
}
