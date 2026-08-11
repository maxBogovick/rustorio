package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Item layer: the cargo a building is holding "in transit" ({@code Building.heldItem()}) — on a
 * belt, in a miner's hands, in a splitter, underground. A small shape is drawn above the
 * building's own sprite every tick, directly reflecting {@code heldItem()}: the moment cargo
 * leaves a miner's hands, it disappears in the same tick.
 *
 * <p>A shape with a dark outline rather than a real item texture: the placeholder art in {@code
 * resources/} is 3x4/4x4-pixel and indistinguishable at any scale between a chain's ore, plate and
 * gear.
 *
 * <p><b>Silhouette + letter, not color alone (X-06, DEV_TASKS.md).</b> Color used to be the ONLY
 * channel — an accessibility failure the card calls out directly: {@code ITEM_IRON_ORE} and {@code
 * ITEM_ALLOY_PLATE} are both close, unremarkable grays, indistinguishable to a colorblind player.
 * {@link Palette#itemShape} groups items by production role (raw ore = circle, a furnace's flat
 * plate = square, anything a press assembles = triangle) — three shapes for eleven items still
 * leaves several looking alike within a group, so {@link #letterFor} draws each item's own first
 * letter on top, in whichever of black/white actually contrasts against that item's fill color.
 * Between shape, letter and color, no two items share all three.
 */
final class ItemRenderer {

    /** Диаметр/сторона фигуры меньше клетки — чтобы отличаться от здания под ним, а не перекрывать его. */
    private static final float DIAMETER_SCALE = 0.46f;
    /** Тёмная обводка — то, что превращает плоское пятно в узнаваемый «предмет» с краем. */
    private static final Color OUTLINE = new Color(0f, 0f, 0f, 0.55f);

    /** One piece of visible cargo, already in screen coordinates — collected once, drawn three times (fill, outline, letter). */
    private record Cargo(float x, float y, ItemType item) {
    }

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Grid grid;

    ItemRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.grid = grid;
    }

    /**
     * Only visits buildings within {@code visible} (P4-04, BUG_FIX_PROGRESS.md) instead of the
     * whole map, and collects their cargo into {@link #cargoIn} ONCE — the fill pass, the outline
     * pass (separate {@code ShapeRenderer.ShapeType}s, can't be mixed into one {@code begin}/{@code
     * end}) and the letter pass ({@code SpriteBatch}, can't be mixed with {@code ShapeRenderer} at
     * all) all read the same list instead of each re-walking the world.
     */
    void render(World world, TileRange visible) {
        float radius = GfxConfig.TILE * DIAMETER_SCALE / 2f;
        List<Cargo> cargo = cargoIn(world, visible);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Cargo c : cargo) {
            shapes.setColor(Palette.itemColor(c.item()));
            drawShape(Palette.itemShape(c.item()), c.x(), c.y(), radius);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(OUTLINE);
        for (Cargo c : cargo) {
            drawShape(Palette.itemShape(c.item()), c.x(), c.y(), radius);
        }
        shapes.end();

        batch.begin();
        font.getData().setScale(0.5f);
        for (Cargo c : cargo) {
            font.setColor(letterColor(c.item()));
            font.draw(batch, letterFor(c.item()), c.x() - 3f, c.y() + 3.5f);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawShape(ItemShape shape, float cx, float cy, float radius) {
        switch (shape) {
            case CIRCLE -> shapes.circle(cx, cy, radius, 20);
            case SQUARE -> shapes.rect(cx - radius, cy - radius, radius * 2f, radius * 2f);
            case TRIANGLE -> {
                float top = radius * 1.05f;
                float bottom = radius * 0.85f;
                shapes.triangle(cx, cy + top, cx - radius, cy - bottom, cx + radius, cy - bottom);
            }
        }
    }

    /** First letter of the item's label — see the class javadoc for why this, on top of shape, is still needed. */
    private static String letterFor(ItemType item) {
        return item.label().substring(0, 1);
    }

    /** Whichever of black/white actually reads against this item's own fill color, by relative luminance. */
    private static Color letterColor(ItemType item) {
        Color fill = Palette.itemColor(item);
        float luminance = 0.299f * fill.r + 0.587f * fill.g + 0.114f * fill.b;
        return luminance > 0.55f ? Color.BLACK : Color.WHITE;
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
