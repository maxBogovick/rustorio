package com.graphics.render;

import com.rustorio.domain.building.BuildingPrototype;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Geometry AND filtering logic for the build menu (Phase 8) — one formula shared by drawing
 * ({@link BuildMenuRenderer}) and hit-testing ({@code com.graphics.input.InputHandler}, a
 * different package — same reason {@link HotbarLayout} is public).
 *
 * <p>No scrolling — {@link #MAX_VISIBLE_ROWS} simply truncates a match list longer than that; a
 * real "300 buildings, no way to see the rest" problem would need pagination or a scrollbar, but
 * search + per-mod category already narrows a large registry to a handful in practice, and
 * building a scroll widget nothing else in this project has is exactly the "abstraction for a
 * future that isn't this card's job" the design checklist warns against.
 */
public final class BuildMenuLayout {

    static final float PADDING = 24f;
    static final float TITLE_HEIGHT = 56f;
    static final float ROW_HEIGHT = 24f;
    static final float PANEL_WIDTH = 560f;
    /** How many match rows fit on the panel without scrolling — see the class javadoc. */
    public static final int MAX_VISIBLE_ROWS = 16;

    private BuildMenuLayout() {
    }

    /** Category = the prototype's own {@link com.rustorio.api.content.ContentId} namespace (which mod registered it) — sorted for determinism, no duplicates. See the Phase 8 intro in ENGINE_TASKS.md for why not a hand-picked taxonomy. */
    public static List<String> categories(List<BuildingPrototype> all) {
        return all.stream().map(p -> p.id().namespace()).distinct().sorted().toList();
    }

    /**
     * Turns the raw, ever-growing TAB counter ({@code com.graphics.input.SimulationControls}'s own
     * field) into an actual category: {@code null} ("all") at cycle 0, then each of {@code
     * categories} in turn, wrapping — {@link Math#floorMod} so a stray negative cycle (shouldn't
     * happen, but see no reason to crash on one) still resolves to a valid index instead of
     * throwing.
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

    /** The visible slice of {@code matches} — truncated to {@link #MAX_VISIBLE_ROWS}, never longer. */
    public static List<BuildingPrototype> visibleRows(List<BuildingPrototype> matches) {
        return matches.size() > MAX_VISIBLE_ROWS ? matches.subList(0, MAX_VISIBLE_ROWS) : matches;
    }

    static float panelX(int screenWidth) {
        return (screenWidth - PANEL_WIDTH) / 2f;
    }

    static float panelHeight(int visibleRowCount) {
        return PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * Math.max(1, visibleRowCount);
    }

    static float panelY(int screenHeight, int visibleRowCount) {
        return (screenHeight - panelHeight(visibleRowCount)) / 2f;
    }

    /** Baseline Y (HUD coordinates, from the bottom) for row {@code index} — {@code -1} is the "showing first N" hint line just above row 0. */
    static float rowY(float panelY, float panelH, int index) {
        return panelY + panelH - PADDING - TITLE_HEIGHT - ROW_HEIGHT * (index + 1);
    }

    /**
     * Which currently-visible row (0-based, top to bottom) sits under {@code (screenX, screenY)} —
     * screen coordinates as {@code Gdx.input} gives them (Y from the TOP), same flip {@link
     * HotbarLayout#hitTest} already does — or {@code -1} if the click missed every row (including
     * "inside the panel but on the title/search text, not a row").
     */
    public static int hitTestRow(float screenX, float screenY, int screenWidth, int screenHeight, int visibleRowCount) {
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(visibleRowCount);
        float panelY = panelY(screenHeight, visibleRowCount);
        float hudY = screenHeight - screenY;
        if (screenX < panelX || screenX > panelX + PANEL_WIDTH) {
            return -1;
        }
        for (int i = 0; i < visibleRowCount; i++) {
            float top = rowY(panelY, panelH, i) + ROW_HEIGHT * 0.7f;
            float bottom = top - ROW_HEIGHT;
            if (hudY <= top && hudY >= bottom) {
                return i;
            }
        }
        return -1;
    }

    /** Whether {@code (screenX, screenY)} lands anywhere inside the panel at all — used to swallow a click that hit the panel but no specific row, so it doesn't leak into the world underneath. */
    public static boolean isOverPanel(float screenX, float screenY, int screenWidth, int screenHeight, int visibleRowCount) {
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(visibleRowCount);
        float panelY = panelY(screenHeight, visibleRowCount);
        float hudY = screenHeight - screenY;
        return screenX >= panelX && screenX <= panelX + PANEL_WIDTH && hudY >= panelY && hudY <= panelY + panelH;
    }
}
