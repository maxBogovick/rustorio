package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.Recipe;

/**
 * Книга рецептов: полупрозрачная панель поверх экрана со списком {@link Recipe#ALL} — открывается
 * и закрывается клавишей TAB ({@code com.graphics.input.InputHandler}).
 *
 * <p>Ни один рецепт здесь не перечислен руками. Рендер читает {@link Recipe#ALL} — тот же список
 * данных, по которому печь и пресс сами подбирают себе рецепт ({@link Recipe#find}). Новый рецепт
 * становится виден игроку в тот момент, когда становится доступен в игре, — строкой в
 * {@code Recipe.ALL}, без единой правки этого файла (тот же приём, что у панели построек снизу,
 * читающей {@code BuildingType.values()}, — см. {@link HotbarLayout}).
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

    void render() {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * Recipe.ALL.size();
        float panelX = (screenW - PANEL_WIDTH) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float firstRowY = panelY + panelH - PADDING - TITLE_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        // Кружок-иконка входа рецепта — тот же цветовой язык, что и у груза на ленте
        // (см. Palette#itemColor), чтобы не заводить для книги рецептов отдельную систему меток.
        float y = firstRowY;
        for (Recipe recipe : Recipe.ALL) {
            shapes.setColor(Palette.itemColor(recipe.input()));
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
        for (Recipe recipe : Recipe.ALL) {
            font.draw(batch, describe(recipe), textX, y);
            y -= ROW_HEIGHT;
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** «IRON_ORE -> IRON_PLATE   (Furnace, 5 ticks)» / «ENGINE + GEAR -> CHASSIS   (Press, 15 ticks)». */
    private static String describe(Recipe recipe) {
        String inputs = recipe.input().name()
                + (recipe.input2() == null ? "" : " + " + recipe.input2().name());
        return inputs + "  ->  " + recipe.output().name()
                + "   (" + recipe.type().label() + ", " + recipe.time() + " ticks)";
    }
}
