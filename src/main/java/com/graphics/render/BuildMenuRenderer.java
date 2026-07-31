package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.building.BuildingPrototype;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Build menu (Phase 8): every registered building prototype, vanilla or modded — the roadmap's own
 * "меню строительства с категориями и поиском вместо 9 клавиш". Opened/closed with {@code B}
 * ({@code com.graphics.input.InputHandler}); while open, letter keys type into a search filter and
 * {@code TAB} cycles which mod's namespace is shown (see {@code SimulationControls} for why).
 * Selecting a row (click) pins that prototype into whichever hotbar slot is currently selected —
 * see {@code InputHandler#pinSelectedIntoHotbar}. All geometry/filtering logic lives in {@link
 * BuildMenuLayout}, shared with the input layer's hit-testing.
 */
final class BuildMenuRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    BuildMenuRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(Registry<BuildingPrototype> buildings, String query, int categoryCycle) {
        List<BuildingPrototype> all = buildings.iterate();
        List<String> categories = BuildMenuLayout.categories(all);
        String activeCategory = BuildMenuLayout.activeCategory(categories, categoryCycle);
        List<BuildingPrototype> matches = BuildMenuLayout.filter(all, activeCategory, query);
        List<BuildingPrototype> visible = BuildMenuLayout.visibleRows(matches);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = BuildMenuLayout.panelHeight(visible.size());
        float panelX = BuildMenuLayout.panelX(screenW);
        float panelY = BuildMenuLayout.panelY(screenH, visible.size());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, BuildMenuLayout.PANEL_WIDTH, panelH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Build menu  (B to close)", panelX + BuildMenuLayout.PADDING, panelY + panelH - BuildMenuLayout.PADDING);

        font.getData().setScale(0.75f);
        font.setColor(Palette.HINT);
        String categoryLabel = activeCategory == null ? "all" : activeCategory;
        font.draw(batch, "Category (TAB): " + categoryLabel + " [" + categories.size() + " total]",
                panelX + BuildMenuLayout.PADDING, panelY + panelH - BuildMenuLayout.PADDING - 20f);
        font.draw(batch, "Search: " + query + "_", panelX + BuildMenuLayout.PADDING, panelY + panelH - BuildMenuLayout.PADDING - 38f);
        if (matches.isEmpty()) {
            font.draw(batch, "(no matches)", panelX + BuildMenuLayout.PADDING, BuildMenuLayout.rowY(panelY, panelH, 0));
        } else if (matches.size() > BuildMenuLayout.MAX_VISIBLE_ROWS) {
            font.draw(batch, "(showing first " + BuildMenuLayout.MAX_VISIBLE_ROWS + " of " + matches.size() + " — narrow the search)",
                    panelX + BuildMenuLayout.PADDING, BuildMenuLayout.rowY(panelY, panelH, -1));
        }

        font.getData().setScale(0.8f);
        for (int i = 0; i < visible.size(); i++) {
            BuildingPrototype prototype = visible.get(i);
            font.setColor(Color.WHITE);
            font.draw(batch, prototype.label() + "   (" + prototype.id() + ")   cost: "
                            + prototype.cost().amount() + " " + prototype.cost().item().label(),
                    panelX + BuildMenuLayout.PADDING, BuildMenuLayout.rowY(panelY, panelH, i));
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }
}
