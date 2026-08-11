package com.graphics.render;

import com.graphics.GfxConfig;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaCategories;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The always-visible build panel: a row of category tabs with that category's buildings under it,
 * living in the bottom HUD band to the right of the quick bar.
 *
 * <p>What it replaced, and why: the band used to be one flat strip holding EVERY registered
 * prototype. That is fine at twelve and unreadable at twenty; at twenty-nine it ran off both edges
 * of the screen, which is what a panel that grows with every installed mod eventually does. Tabs
 * turn "more content" into "a fuller tab" instead of "a wider panel".
 *
 * <p>Grouping is by {@link VanillaCategories} — what a building is FOR. The old build menu grouped
 * by {@code id().namespace()}, which is who WROTE it: a player looking for something that moves
 * fluid does not think "waterworks".
 *
 * <p>Geometry only, no libGDX: drawing ({@link HudRenderer}) and hit-testing ({@code
 * com.graphics.input.InputHandler}) share one formula, the same arrangement {@link QuickBarLayout}
 * follows and for the same reason.
 */
public final class CategoryTabsLayout {

    /** Where the panel starts, clearing the quick bar's three columns in the corner. */
    public static final float LEFT = QuickBarLayout.MARGIN_LEFT
            + QuickBarLayout.totalWidth(QuickBarLayout.COLUMNS) + 18f;

    public static final float TAB_WIDTH = 104f;
    public static final float TAB_HEIGHT = 24f;
    public static final float TAB_GAP = 4f;

    public static final float ICON_SIZE = 44f;
    public static final float ICON_GAP = 4f;

    /** Bottom edge of the icon row, and of the panel as a whole. */
    public static final float ICONS_Y = 14f;
    /** Bottom edge of the tab row — directly above the icons it labels. */
    public static final float TABS_Y = ICONS_Y + ICON_SIZE + 4f;

    /**
     * Identity-keyed cache: {@link com.rustorio.api.registry.Registry#iterate()} returns the same
     * frozen list for the whole session, and both {@code InputHandler} and {@link HudRenderer} call
     * {@link #byCategory} every frame. Rebuilding the map twice per frame was pure waste once the
     * registry can no longer grow.
     */
    private static @Nullable List<BuildingPrototype> cachedAll;
    private static @Nullable Map<ContentId, List<BuildingPrototype>> cachedGrouped;

    private CategoryTabsLayout() {
    }

    /**
     * Every category that has at least one building, in {@link VanillaCategories#all} order, with
     * any category a mod invented appended after them.
     *
     * <p>An EMPTY category gets no tab: with no mods installed there is nothing under "Other", and
     * a tab that opens onto nothing is a dead click. A {@link LinkedHashMap} keyed in that fixed
     * order rather than a {@code Set} — tab order that varies between runs would move tabs under
     * the player's cursor between launches, which this project keeps a rule about.
     */
    public static Map<ContentId, List<BuildingPrototype>> byCategory(List<BuildingPrototype> all) {
        if (all == cachedAll && cachedGrouped != null) {
            return cachedGrouped;
        }
        Map<ContentId, List<BuildingPrototype>> grouped = new LinkedHashMap<>();
        for (ContentId category : VanillaCategories.all()) {
            grouped.put(category, new ArrayList<>());
        }
        for (BuildingPrototype prototype : all) {
            grouped.computeIfAbsent(VanillaCategories.of(prototype), key -> new ArrayList<>())
                    .add(prototype);
        }
        grouped.values().removeIf(List::isEmpty);
        Map<ContentId, List<BuildingPrototype>> frozen = new LinkedHashMap<>();
        for (Map.Entry<ContentId, List<BuildingPrototype>> entry : grouped.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        // Unmodifiable LinkedHashMap — not Map.copyOf: copyOf randomizes iteration order between
        // JVM runs, and tab order is visible under the player's cursor.
        cachedAll = all;
        cachedGrouped = Collections.unmodifiableMap(frozen);
        return cachedGrouped;
    }

    /** X of the left edge of tab {@code index}. */
    public static float tabX(int index) {
        return LEFT + index * (TAB_WIDTH + TAB_GAP);
    }

    /** X of the left edge of icon {@code index} within the active tab's row. */
    public static float iconX(int index) {
        return LEFT + index * (ICON_SIZE + ICON_GAP);
    }

    /**
     * Which tab sits under {@code (screenX, screenY)} — {@code Gdx.input}'s coordinates (Y from the
     * top), flipped here so no caller repeats the conversion.
     *
     * @return the tab index, or {@code -1} for a miss
     */
    public static int hitTestTab(float screenX, float screenY, int screenHeight, int tabCount) {
        float hudY = screenHeight - screenY;
        if (hudY < TABS_Y || hudY > TABS_Y + TAB_HEIGHT) {
            return -1;
        }
        for (int i = 0; i < tabCount; i++) {
            float x = tabX(i);
            if (screenX >= x && screenX <= x + TAB_WIDTH) {
                return i;
            }
        }
        return -1;
    }

    /** Which building icon of the active tab sits under the point, or {@code -1} — same coordinate flip as {@link #hitTestTab}. */
    public static int hitTestIcon(float screenX, float screenY, int screenHeight, int iconCount) {
        float hudY = screenHeight - screenY;
        if (hudY < ICONS_Y || hudY > ICONS_Y + ICON_SIZE) {
            return -1;
        }
        for (int i = 0; i < iconCount; i++) {
            float x = iconX(i);
            if (screenX >= x && screenX <= x + ICON_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * How many icons of a category fit before the window's right edge. Everything past that is not
     * drawn — a deliberate cut rather than a shrink, because shrinking icons to fit is how the old
     * strip became unreadable in the first place. The overflow still has a home: the full catalogue
     * is one keypress away, and this panel is for reaching, not for browsing.
     */
    public static int visibleIcons(int screenWidth, int iconCount) {
        float available = screenWidth - LEFT - InventoryPanelLayout.reservedWidth() - 8f;
        int fits = (int) Math.floor((available + ICON_GAP) / (ICON_SIZE + ICON_GAP));
        return Math.max(0, Math.min(iconCount, fits));
    }

    /** Y of the one-line hint drawn above the tabs — inside the band, so it never floats over the world. */
    public static float hintY() {
        return Math.min(TABS_Y + TAB_HEIGHT + 14f, GfxConfig.HUD_BOTTOM_HEIGHT - 4f);
    }
}
