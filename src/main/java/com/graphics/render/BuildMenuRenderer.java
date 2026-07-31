package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.building.BuildingPrototype;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Build menu: every registered building prototype, vanilla or modded — the roadmap's own
 * "меню строительства с категориями и поиском вместо 9 клавиш". Opened/closed with {@code B}
 * ({@code com.graphics.input.InputHandler}); while open, letter/digit/space keys type into a
 * search filter, category tabs are clickable directly (TAB also cycles them for keyboard-only
 * use), and the mouse wheel pages through a match list longer than one screen.
 *
 * <p>An icon grid, not a text list — the same sprite the hotbar already draws for each prototype
 * ({@link Textures#forSprite}), so recognizing a building here takes exactly as long as it does in
 * the hotbar. Hovering a tile highlights it and shows its full name/id/cost on the detail line
 * below the grid, so a click is never blind; the currently-equipped prototype (whatever {@code
 * selected} the hotbar highlights) stays highlighted here too even while nothing is hovered.
 * Clicking a tile pins that prototype into whichever hotbar slot is currently selected — see
 * {@code InputHandler#pinSelectedIntoHotbar}. All geometry/filtering logic lives in {@link
 * BuildMenuLayout}, shared with the input layer's hit-testing.
 */
final class BuildMenuRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;

    BuildMenuRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
    }

    void render(Registry<BuildingPrototype> buildings, ContentId selected, String query, int categoryCycle,
            int scrollOffset) {
        List<BuildingPrototype> all = buildings.iterate();
        List<String> categories = BuildMenuLayout.categories(all);
        int tabCount = categories.size() + 1;
        int activeTabIndex = Math.floorMod(categoryCycle, tabCount);
        String activeCategory = BuildMenuLayout.activeCategory(categories, categoryCycle);
        List<BuildingPrototype> matches = BuildMenuLayout.filter(all, activeCategory, query);
        int clampedScroll = BuildMenuLayout.clampScrollRows(matches.size(), scrollOffset);
        List<BuildingPrototype> visible = BuildMenuLayout.visibleTiles(matches, clampedScroll);
        int visibleRows = BuildMenuLayout.visibleTileRows(visible.size());

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = BuildMenuLayout.panelHeight(visibleRows);
        float panelX = BuildMenuLayout.panelX(screenW);
        float panelY = BuildMenuLayout.panelY(screenH, visibleRows);
        int hoveredIndex = BuildMenuLayout.hitTestTile(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH, visible.size());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, BuildMenuLayout.PANEL_WIDTH, panelH);
        for (int i = 0; i < tabCount; i++) {
            shapes.setColor(Palette.SLOT_BG);
            shapes.rect(BuildMenuLayout.tabX(i, panelX, tabCount), BuildMenuLayout.tabsY(panelY, panelH),
                    BuildMenuLayout.tabWidth(tabCount), BuildMenuLayout.TABS_HEIGHT);
        }
        for (int i = 0; i < visible.size(); i++) {
            int row = i / BuildMenuLayout.COLUMNS;
            int col = i % BuildMenuLayout.COLUMNS;
            shapes.setColor(Palette.SLOT_BG);
            shapes.rect(BuildMenuLayout.tileX(col, panelX), BuildMenuLayout.tileY(panelY, panelH, row),
                    BuildMenuLayout.TILE_SIZE, BuildMenuLayout.TILE_SIZE);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        // Тонкая грань вокруг всей панели (арт-редизайн) — раньше подложка была голым
        // прямоугольником без края и «плавала» поверх сцены без визуальной опоры.
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, BuildMenuLayout.PANEL_WIDTH, panelH);
        for (int i = 0; i < tabCount; i++) {
            boolean active = i == activeTabIndex;
            shapes.setColor(active ? Palette.SLOT_SELECTED : Palette.SLOT_BORDER);
            float x = BuildMenuLayout.tabX(i, panelX, tabCount);
            float y = BuildMenuLayout.tabsY(panelY, panelH);
            float w = BuildMenuLayout.tabWidth(tabCount);
            shapes.rect(x, y, w, BuildMenuLayout.TABS_HEIGHT);
            if (active) {
                // Same double-outline trick the hotbar uses for its own selected slot (HudRenderer)
                // — a single-pixel line is too faint next to a neighbor's ordinary border.
                shapes.rect(x + 1, y + 1, w - 2, BuildMenuLayout.TABS_HEIGHT - 2);
            }
        }
        for (int i = 0; i < visible.size(); i++) {
            int row = i / BuildMenuLayout.COLUMNS;
            int col = i % BuildMenuLayout.COLUMNS;
            boolean isSelected = visible.get(i).id().equals(selected);
            boolean isHovered = i == hoveredIndex;
            float x = BuildMenuLayout.tileX(col, panelX);
            float y = BuildMenuLayout.tileY(panelY, panelH, row);
            shapes.setColor(isSelected ? Palette.SLOT_SELECTED : isHovered ? Palette.TILE_HOVER : Palette.SLOT_BORDER);
            shapes.rect(x, y, BuildMenuLayout.TILE_SIZE, BuildMenuLayout.TILE_SIZE);
            if (isSelected) {
                shapes.rect(x + 1, y + 1, BuildMenuLayout.TILE_SIZE - 2, BuildMenuLayout.TILE_SIZE - 2);
            }
        }
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Build menu  (B to close)", panelX + BuildMenuLayout.PADDING, panelY + panelH - BuildMenuLayout.PADDING);

        font.getData().setScale(0.75f);
        for (int i = 0; i < tabCount; i++) {
            String label = i == 0 ? "All" : categories.get(i - 1);
            font.setColor(i == activeTabIndex ? Palette.SLOT_SELECTED : Palette.HINT);
            float x = BuildMenuLayout.tabX(i, panelX, tabCount);
            float y = BuildMenuLayout.tabsY(panelY, panelH);
            font.draw(batch, label, x + 8f, y + BuildMenuLayout.TABS_HEIGHT - 7f);
        }

        float searchY = BuildMenuLayout.tabsY(panelY, panelH) - BuildMenuLayout.SEARCH_HEIGHT + 6f;
        font.setColor(Palette.HINT);
        font.draw(batch, "Search: " + query + "_", panelX + BuildMenuLayout.PADDING, searchY);

        float hintY = searchY - BuildMenuLayout.HINT_HEIGHT + 4f;
        font.draw(batch, hintText(matches.size(), visible.size(), clampedScroll), panelX + BuildMenuLayout.PADDING, hintY);

        font.getData().setScale(0.62f);
        float labelReserve = 14f; // strip along the tile's bottom edge the label line sits in
        float iconPad = 4f;
        float iconSize = BuildMenuLayout.TILE_SIZE - labelReserve - iconPad * 2;
        for (int i = 0; i < visible.size(); i++) {
            int row = i / BuildMenuLayout.COLUMNS;
            int col = i % BuildMenuLayout.COLUMNS;
            BuildingPrototype prototype = visible.get(i);
            float x = BuildMenuLayout.tileX(col, panelX);
            float y = BuildMenuLayout.tileY(panelY, panelH, row);
            TextureRegion icon = textures.forSprite(prototype.texture());
            font.setColor(Color.WHITE);
            batch.draw(icon, x + (BuildMenuLayout.TILE_SIZE - iconSize) / 2f, y + labelReserve + iconPad, iconSize, iconSize);
            font.setColor(prototype.id().equals(selected) ? Palette.SLOT_SELECTED : Palette.HINT);
            font.draw(batch, prototype.label(), x + 3f, y + 11f);
        }

        font.getData().setScale(0.7f);
        font.setColor(Palette.HINT);
        BuildingPrototype detail = hoveredIndex >= 0 ? visible.get(hoveredIndex) : equipped(all, selected);
        String detailText = detail == null ? "Hover or click a building" : detail.label() + "   (" + detail.id() + ")   cost: "
                + detail.cost().amount() + " " + detail.cost().item().label();
        font.draw(batch, detailText, panelX + BuildMenuLayout.PADDING, panelY + BuildMenuLayout.DETAIL_HEIGHT);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** The prototype currently pinned into the active hotbar slot — {@code null} only if the registry no longer has it (shouldn't happen, but a stale-content crash on opening the menu is worse than a blank detail line). */
    private static @Nullable BuildingPrototype equipped(List<BuildingPrototype> all, ContentId selected) {
        return all.stream().filter(p -> p.id().equals(selected)).findFirst().orElse(null);
    }

    /** What the hint line under the search box says — page position if there's more than one page, a plain count otherwise, or the no-matches case. */
    private static String hintText(int matchCount, int visibleCount, int scrollRows) {
        if (matchCount == 0) {
            return "(no matches)";
        }
        if (matchCount == visibleCount && scrollRows == 0) {
            return matchCount + (matchCount == 1 ? " match" : " matches");
        }
        int shownStart = scrollRows * BuildMenuLayout.COLUMNS + 1;
        int shownEnd = shownStart + visibleCount - 1;
        return "Showing " + shownStart + "-" + shownEnd + " of " + matchCount + " — scroll for more";
    }
}
