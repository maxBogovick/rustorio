package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Recipe book: a translucent panel listing every recipe in a {@link RecipeBook} — opened and
 * closed with TAB ({@code com.graphics.input.InputHandler}).
 *
 * <p>No recipe is listed here by hand. This reads whichever {@link RecipeBook} the running world
 * was actually built with — the same registry furnaces and presses search through ({@link
 * RecipeBook#find}) — so a new recipe becomes visible the moment it's playable, as one constant
 * there, with no change to this file (the same trick the hotbar below uses, reading {@code
 * BuildingType.values()} — see {@link CategoryTabsLayout}).
 *
 * <p><b>{@link #MAX_VISIBLE_RECIPES} (live bug report).</b> Recipes are JSON content now, loaded
 * through the same open, moddable registry as buildings ({@code com.rustorio.mod}) — the exact
 * risk {@link BuildMenuLayout}'s own {@code MAX_VISIBLE_TILES} was already hardened against for
 * buildings. Before this, a mod registering enough recipes drew a panel taller than the window
 * with no visible cue anything was missing (the bottom rows simply ran off-screen); this truncates
 * the same way the build menu already does, with the same honest "showing first N of M" line
 * rather than silently hiding the rest.
 */
final class RecipeBookRenderer {

    private static final float PADDING = 24f;
    private static final float TITLE_HEIGHT = 30f;
    private static final float ROW_HEIGHT = 26f;
    private static final float ICON_RADIUS = 7f;
    private static final float PANEL_WIDTH = 480f;
    /** How many recipe rows fit on the panel without scrolling — see the class javadoc. */
    static final int MAX_VISIBLE_RECIPES = 20;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    RecipeBookRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(RecipeBook recipeBook) {
        List<Recipe> all = recipeBook.all();
        List<Recipe> visible = visibleRecipes(all);
        boolean truncated = visible.size() < all.size();
        int extraHintRow = truncated ? 1 : 0;

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * (visible.size() + extraHintRow);
        float panelX = (screenW - PANEL_WIDTH) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float firstRowY = panelY + panelH - PADDING - TITLE_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        // Recipe-input icon circle — the same color language as cargo on a belt (see
        // Palette#itemColor), so the recipe book doesn't need its own separate marker system.
        float y = firstRowY;
        for (Recipe recipe : visible) {
            shapes.setColor(Palette.itemColor(recipe.ingredients().get(0)));
            shapes.circle(panelX + PADDING + ICON_RADIUS, y + 3, ICON_RADIUS, 16);
            y -= ROW_HEIGHT;
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Recipe book  (TAB to close)", panelX + PADDING, panelY + panelH - PADDING);

        font.getData().setScale(0.8f);
        font.setColor(Palette.HINT);
        float textX = panelX + PADDING + ICON_RADIUS * 2 + 10;
        y = firstRowY;
        for (Recipe recipe : visible) {
            font.draw(batch, describe(recipe), textX, y);
            y -= ROW_HEIGHT;
        }
        if (truncated) {
            font.draw(batch, "(showing first " + visible.size() + " of " + all.size() + " recipes)", panelX + PADDING, y);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** The visible slice of {@code all} — truncated to {@link #MAX_VISIBLE_RECIPES}, never longer. Package-private, pure — no libGDX — so a JUnit test can pin it down without a window, same reason {@link BuildMenuLayout#visibleTiles} is public. */
    static List<Recipe> visibleRecipes(List<Recipe> all) {
        return all.size() > MAX_VISIBLE_RECIPES ? all.subList(0, MAX_VISIBLE_RECIPES) : all;
    }

    /** "Iron Ore -> Iron Plate   (Furnace, 5 ticks)" / "Engine + Gear -> Chassis   (Press, 15 ticks)". */
    private static String describe(Recipe recipe) {
        String inputs = recipe.ingredients().stream().map(ItemType::label).collect(Collectors.joining(" + "));
        return inputs + "  ->  " + recipe.output().label()
                + "   (" + kindLabel(recipe.type()) + ", " + recipe.time() + " ticks)";
    }

    /**
     * A recipe's {@code type()} is an open {@link ContentId} now (a JSON-defined custom archetype
     * names its own, private one — see {@link Recipe}'s own javadoc), not always one of the 12
     * vanilla {@link BuildingType} constants that has a nice display {@code label()}. Reverse-looks
     * up a matching vanilla constant for the common case; falls back to the bare {@code path} (a
     * custom kind's own name, e.g. {@code "voron"}) when none matches.
     */
    private static String kindLabel(ContentId kind) {
        for (BuildingType type : BuildingType.values()) {
            if (type.contentId().equals(kind)) {
                return type.label();
            }
        }
        return kind.path();
    }
}
