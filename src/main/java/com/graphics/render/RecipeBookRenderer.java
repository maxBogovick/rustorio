package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import java.util.stream.Collectors;

/**
 * Recipe book: a translucent panel listing every recipe in a {@link RecipeBook} — opened and
 * closed with TAB ({@code com.graphics.input.InputHandler}).
 *
 * <p>No recipe is listed here by hand. This reads whichever {@link RecipeBook} the running world
 * was actually built with — the same registry furnaces and presses search through ({@link
 * RecipeBook#find}) — so a new recipe becomes visible the moment it's playable, as one constant
 * there, with no change to this file (the same trick the hotbar below uses, reading {@code
 * BuildingType.values()} — see {@link HotbarLayout}).
 */
final class RecipeBookRenderer {

    private static final float PADDING = 24f;
    private static final float TITLE_HEIGHT = 30f;
    private static final float ROW_HEIGHT = 26f;
    private static final float ICON_RADIUS = 7f;
    private static final float PANEL_WIDTH = 480f;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    RecipeBookRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(RecipeBook recipeBook) {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * recipeBook.all().size();
        float panelX = (screenW - PANEL_WIDTH) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float firstRowY = panelY + panelH - PADDING - TITLE_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        // Recipe-input icon circle — the same color language as cargo on a belt (see
        // Palette#itemColor), so the recipe book doesn't need its own separate marker system.
        float y = firstRowY;
        for (Recipe recipe : recipeBook.all()) {
            shapes.setColor(Palette.itemColor(recipe.ingredients().get(0)));
            shapes.circle(panelX + PADDING + ICON_RADIUS, y + 3, ICON_RADIUS, 16);
            y -= ROW_HEIGHT;
        }
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Recipe book  (TAB to close)", panelX + PADDING, panelY + panelH - PADDING);

        font.getData().setScale(0.8f);
        font.setColor(Palette.HINT);
        float textX = panelX + PADDING + ICON_RADIUS * 2 + 10;
        y = firstRowY;
        for (Recipe recipe : recipeBook.all()) {
            font.draw(batch, describe(recipe), textX, y);
            y -= ROW_HEIGHT;
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** "Iron Ore -> Iron Plate   (Furnace, 5 ticks)" / "Engine + Gear -> Chassis   (Press, 15 ticks)". */
    private static String describe(Recipe recipe) {
        String inputs = recipe.ingredients().stream().map(ItemType::label).collect(Collectors.joining(" + "));
        return inputs + "  ->  " + recipe.output().label()
                + "   (" + recipe.type().label() + ", " + recipe.time() + " ticks)";
    }
}
