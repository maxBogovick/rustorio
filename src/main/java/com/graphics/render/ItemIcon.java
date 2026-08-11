package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.model.ItemType;

/**
 * One item's identity icon on a flat HUD panel — shape + letter + color, the same three-channel
 * language {@link ItemRenderer} already draws for cargo in transit on the map (see that class's
 * own javadoc for why color alone isn't enough). {@link HudRenderer}'s produced preview and {@link
 * InfoOverlayRenderer}'s full grids both need the same identity language off the map, so it lives
 * here instead of a third copy of {@link ItemRenderer}'s private version — that one stays as-is,
 * batched around world cargo it collects itself, a different enough shape of problem to leave alone.
 *
 * <p>Three passes (fill / outline / letter), not one self-contained draw call: {@code ShapeRenderer}
 * can't mix {@code Filled} and {@code Line} in one {@code begin}/{@code end}, and {@code
 * SpriteBatch} can't be active at the same time as either — exactly {@link ItemRenderer}'s own
 * constraint. Callers loop their own chip list once per pass inside their own already-open
 * begin/end block, same as {@link ItemRenderer#render} does for map cargo.
 */
final class ItemIcon {

    private static final Color OUTLINE = new Color(0f, 0f, 0f, 0.55f);

    private ItemIcon() {
    }

    /**
     * Fill pass — call once per icon between the caller's own {@code shapes.begin(Filled)}/{@code
     * end()}. Draws a light {@link Palette#HINT} halo behind the item's own shape before filling
     * it, a live bug report: {@link Palette#itemColor} was picked for contrast against cargo on a
     * belt or ore on the ground, never against the near-black HUD panel these icons actually sit
     * on ({@link Palette#PANEL_BG}) — {@code COAL}/{@code IRON_ORE}'s own dark grays are close
     * enough to that background to disappear entirely without the halo (exactly what got reported:
     * a produced-preview icon with no visible dot at all, just its count).
     */
    static void fill(ShapeRenderer shapes, ItemType item, float cx, float cy, float radius) {
        shapes.setColor(Palette.HINT);
        drawShape(shapes, Palette.itemShape(item), cx, cy, radius + 1.5f);
        shapes.setColor(Palette.itemColor(item));
        drawShape(shapes, Palette.itemShape(item), cx, cy, radius);
    }

    /** Outline pass — call once per icon between the caller's own {@code shapes.begin(Line)}/{@code end()}. */
    static void outline(ShapeRenderer shapes, ItemType item, float cx, float cy, float radius) {
        shapes.setColor(OUTLINE);
        drawShape(shapes, Palette.itemShape(item), cx, cy, radius);
    }

    /**
     * Letter pass — call once per icon between the caller's own {@code batch.begin()}/{@code
     * end()}, with {@code font}'s scale already set by the caller (a HUD chip and a full-size
     * cargo icon want different letter sizes, so this doesn't assume one).
     */
    static void letter(SpriteBatch batch, BitmapFont font, ItemType item, float cx, float cy, float radius) {
        font.setColor(letterColor(item));
        font.draw(batch, letterFor(item), cx - radius * 0.43f, cy + radius * 0.5f);
    }

    private static void drawShape(ShapeRenderer shapes, ItemShape shape, float cx, float cy, float radius) {
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

    private static String letterFor(ItemType item) {
        return item.label().substring(0, 1);
    }

    /** Whichever of black/white actually reads against this item's own fill color, by relative luminance. */
    private static Color letterColor(ItemType item) {
        Color fill = Palette.itemColor(item);
        float luminance = 0.299f * fill.r + 0.587f * fill.g + 0.114f * fill.b;
        return luminance > 0.55f ? Color.BLACK : Color.WHITE;
    }
}
