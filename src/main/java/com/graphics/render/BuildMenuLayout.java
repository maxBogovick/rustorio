package com.graphics.render;

import com.rustorio.domain.building.BuildingPrototype;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Geometry AND filtering logic for the build menu — one formula shared by drawing
 * ({@link BuildMenuRenderer}) and hit-testing ({@code com.graphics.input.InputHandler}, a
 * different package — same reason {@link QuickBarLayout} is public).
 *
 * <p>An icon grid with tabs, not a text list: category tabs are drawn as actual clickable buttons
 * (fixed-width cells, no font measurement needed here — this class stays libGDX-free so a JUnit
 * test can pin it down without a window), and a match list longer than one page scrolls (mouse
 * wheel, see {@code SimulationControls}'s own scroll offset) instead of being silently cut off.
 */
public final class BuildMenuLayout {

    static final float PADDING = 24f;
    static final float TITLE_HEIGHT = 28f;
    static final float TABS_HEIGHT = 28f;
    static final float SEARCH_HEIGHT = 22f;
    static final float HINT_HEIGHT = 20f;
    static final float DETAIL_HEIGHT = 22f;
    static final float TAB_MAX_WIDTH = 96f;

    /** One grid cell: icon plus its label line underneath. */
    public static final float TILE_SIZE = 64f;
    static final float TILE_GAP = 10f;

    /** Fixed column count — the grid never reflows into more or fewer columns. */
    public static final int COLUMNS = 6;
    /** How many tile ROWS fit on the panel before the rest needs a scroll. */
    public static final int VISIBLE_TILE_ROWS = 4;
    /** One page's worth of tiles — {@link #VISIBLE_TILE_ROWS} scrolls further, see {@link #clampScrollRows}. */
    public static final int MAX_VISIBLE_TILES = COLUMNS * VISIBLE_TILE_ROWS;

    public static final float PANEL_WIDTH = PADDING * 2 + COLUMNS * TILE_SIZE + (COLUMNS - 1) * TILE_GAP;

    private BuildMenuLayout() {
    }

    /** Category = the prototype's own {@link com.rustorio.api.content.ContentId} namespace (which mod registered it), not a hand-picked taxonomy nothing in the domain actually defines — sorted for determinism, no duplicates. */
    public static List<String> categories(List<BuildingPrototype> all) {
        return all.stream().map(p -> p.id().namespace()).distinct().sorted().toList();
    }

    /**
     * Turns the raw, ever-growing TAB counter ({@code com.graphics.input.SimulationControls}'s own
     * field) into an actual category: {@code null} ("all") at cycle 0, then each of {@code
     * categories} in turn, wrapping — {@link Math#floorMod} so a stray negative cycle (shouldn't
     * happen, but see no reason to crash on one) still resolves to a valid index instead of
     * throwing. A tab click sets this same counter directly to the tab's own index, so TAB and
     * clicking a tab agree on what index N means.
     */
    public static @Nullable String activeCategory(List<String> categories, int categoryCycle) {
        if (categories.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(categoryCycle, categories.size() + 1);
        return index == 0 ? null : categories.get(index - 1);
    }

    /** {@code category == null} matches everything; {@code query} is a case-insensitive substring match on {@code label}, blank matches everything. */
    public static List<BuildingPrototype> filter(List<BuildingPrototype> all, @Nullable String category, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(p -> category == null || p.id().namespace().equals(category))
                .filter(p -> needle.isEmpty() || p.label().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /**
     * Clamps a raw scroll offset (rows) to what {@code matchCount} tiles actually has to scroll
     * through — {@code rawScrollRows} comes from {@code SimulationControls}, which only counts
     * wheel notches and has no idea how many matches exist right now (same split of
     * responsibility as {@link #activeCategory}: the counter is raw, this class alone resolves it
     * against the current registry).
     */
    public static int clampScrollRows(int matchCount, int rawScrollRows) {
        int totalRows = (matchCount + COLUMNS - 1) / COLUMNS;
        int maxScroll = Math.max(0, totalRows - VISIBLE_TILE_ROWS);
        return Math.max(0, Math.min(rawScrollRows, maxScroll));
    }

    /** The one page of {@code matches} visible at {@code scrollRows} — {@code scrollRows} must already be {@link #clampScrollRows} output. */
    public static List<BuildingPrototype> visibleTiles(List<BuildingPrototype> matches, int scrollRows) {
        int start = Math.min(matches.size(), scrollRows * COLUMNS);
        int end = Math.min(matches.size(), start + MAX_VISIBLE_TILES);
        return matches.subList(start, end);
    }

    /** How many tile rows {@code visibleTileCount} tiles actually occupy — 0 for an empty page, never more than {@link #VISIBLE_TILE_ROWS}. */
    static int visibleTileRows(int visibleTileCount) {
        return (visibleTileCount + COLUMNS - 1) / COLUMNS;
    }

    static float panelX(int screenWidth) {
        return (screenWidth - PANEL_WIDTH) / 2f;
    }

    static float panelHeight(int visibleTileRowCount) {
        int rows = Math.max(1, visibleTileRowCount);
        return PADDING * 2 + TITLE_HEIGHT + TABS_HEIGHT + SEARCH_HEIGHT + HINT_HEIGHT + DETAIL_HEIGHT
                + TILE_SIZE * rows + TILE_GAP * (rows - 1);
    }

    static float panelY(int screenHeight, int visibleTileRowCount) {
        return (screenHeight - panelHeight(visibleTileRowCount)) / 2f;
    }

    /** Bottom edge (HUD Y) of the tabs row — {@code + TABS_HEIGHT} is its top edge. */
    static float tabsY(float panelY, float panelH) {
        return panelY + panelH - PADDING - TITLE_HEIGHT - TABS_HEIGHT;
    }

    /** Every tab is the same width, shrunk to fit {@code tabCount} of them if {@link #TAB_MAX_WIDTH} each would overflow the panel. */
    static float tabWidth(int tabCount) {
        float available = PANEL_WIDTH - PADDING * 2;
        return Math.min(TAB_MAX_WIDTH, available / Math.max(1, tabCount));
    }

    static float tabX(int index, float panelX, int tabCount) {
        return panelX + PADDING + index * tabWidth(tabCount);
    }

    /**
     * Which tab (0 = "all", 1..N = {@code categories.get(index - 1)}) sits under {@code (screenX,
     * screenY)}, or {@code -1} — same screen-coordinate convention as {@link #hitTestTile}.
     * {@code visibleTileCount} (not rows) so every public hit-test here shares the one number a
     * caller already has to track — how many tiles are on screen right now — rather than each
     * demanding its own derived form of it.
     */
    public static int hitTestTab(float screenX, float screenY, int screenWidth, int screenHeight,
            int visibleTileCount, int tabCount) {
        int visibleRows = visibleTileRows(visibleTileCount);
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(visibleRows);
        float panelY = panelY(screenHeight, visibleRows);
        float hudY = screenHeight - screenY;
        float bottom = tabsY(panelY, panelH);
        if (hudY < bottom || hudY > bottom + TABS_HEIGHT) {
            return -1;
        }
        float tabW = tabWidth(tabCount);
        // Math.floor — same reason as hitTestTile: a bare (int) cast truncates toward zero and
        // would map a point just left of the first tab to index 0 instead of a negative index.
        int index = (int) Math.floor((screenX - (panelX + PADDING)) / tabW);
        return index >= 0 && index < tabCount ? index : -1;
    }

    /** Top edge (HUD Y) of the tile grid — everything below the tabs/search/hint lines. */
    static float gridTop(float panelY, float panelH) {
        return tabsY(panelY, panelH) - SEARCH_HEIGHT - HINT_HEIGHT;
    }

    static float tileX(int col, float panelX) {
        return panelX + PADDING + col * (TILE_SIZE + TILE_GAP);
    }

    /** Bottom edge (HUD Y) of the tile at grid row {@code row} — libGDX draws rects from this corner up. */
    static float tileY(float panelY, float panelH, int row) {
        return gridTop(panelY, panelH) - TILE_SIZE - row * (TILE_SIZE + TILE_GAP);
    }

    /**
     * Which currently-visible tile (0-based, row-major: {@code row * COLUMNS + col}) sits under
     * {@code (screenX, screenY)} — screen coordinates as {@code Gdx.input} gives them (Y from the
     * TOP), same flip {@link QuickBarLayout#hitTest} already does — or {@code -1} if the click missed
     * every tile, including a point in the gap between tiles.
     */
    public static int hitTestTile(float screenX, float screenY, int screenWidth, int screenHeight, int visibleTileCount) {
        if (visibleTileCount <= 0) {
            return -1;
        }
        int visibleRows = visibleTileRows(visibleTileCount);
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(visibleRows);
        float panelY = panelY(screenHeight, visibleRows);
        float hudY = screenHeight - screenY;

        // Math.floor, not a bare (int) cast: a cast truncates TOWARD ZERO, which would map a point
        // less than one tile-step to the left of/above the grid to column/row 0 instead of a
        // negative (and therefore correctly rejected) index.
        int col = (int) Math.floor((screenX - (panelX + PADDING)) / (TILE_SIZE + TILE_GAP));
        if (col < 0 || col >= COLUMNS || screenX > tileX(col, panelX) + TILE_SIZE) {
            return -1; // left of the grid, right of it, or in the gap after this column's tile
        }

        float top = gridTop(panelY, panelH);
        int row = (int) Math.floor((top - hudY) / (TILE_SIZE + TILE_GAP));
        if (row < 0 || row >= visibleRows || hudY < tileY(panelY, panelH, row)) {
            return -1; // above the grid, below it, or in the gap under this row's tile
        }

        int index = row * COLUMNS + col;
        return index < visibleTileCount ? index : -1;
    }

    /** Whether {@code (screenX, screenY)} lands anywhere inside the panel at all — used to swallow a click that hit the panel but no specific tab/tile, so it doesn't leak into the world underneath. {@code visibleTileCount}, same reason as {@link #hitTestTab}. */
    public static boolean isOverPanel(float screenX, float screenY, int screenWidth, int screenHeight, int visibleTileCount) {
        int visibleRows = visibleTileRows(visibleTileCount);
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(visibleRows);
        float panelY = panelY(screenHeight, visibleRows);
        float hudY = screenHeight - screenY;
        return screenX >= panelX && screenX <= panelX + PANEL_WIDTH && hudY >= panelY && hudY <= panelY + panelH;
    }
}
