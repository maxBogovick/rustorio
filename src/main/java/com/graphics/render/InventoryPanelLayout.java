package com.graphics.render;

import com.graphics.GfxConfig;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.world.PlayerInventoryView;
import java.util.ArrayList;
import java.util.List;

/**
 * Always-on player inventory grid in the bottom-right of the HUD band — the counterpart to
 * {@link QuickBarLayout}'s bottom-left corner.
 *
 * <p>Geometry only, no libGDX: {@link HudRenderer} draws it and {@code InputHandler} hit-tests it
 * from the same formulas, for the same reason {@link QuickBarLayout} exists.
 *
 * <p>Only stacks with {@code amount > 0} get a cell. Order follows the item registry's iteration
 * order (registration / rawId), never a {@code HashMap} key set — visible under the cursor, so
 * this project keeps a rule about that.
 */
public final class InventoryPanelLayout {

    public static final float CELL_SIZE = QuickBarLayout.CELL_SIZE;
    public static final float CELL_GAP = QuickBarLayout.CELL_GAP;
    public static final float MARGIN_RIGHT = 14f;
    public static final float MARGIN_BOTTOM = QuickBarLayout.MARGIN_BOTTOM;

    public static final int COLUMNS = 3;
    public static final int MAX_ROWS = 3;
    public static final int MAX_CELLS = COLUMNS * MAX_ROWS;

    /** Gap between the category icon strip and this panel — keeps tabs from eating inventory cells. */
    public static final float GAP_FROM_BUILD_PANEL = 16f;

    private InventoryPanelLayout() {
    }

    /** Width reserved on the right so {@link CategoryTabsLayout#visibleIcons} stops before this panel. */
    public static float reservedWidth() {
        return totalWidth(COLUMNS) + MARGIN_RIGHT + GAP_FROM_BUILD_PANEL;
    }

    public static float totalWidth(int columns) {
        return columns * CELL_SIZE + (columns - 1) * CELL_GAP;
    }

    public static float totalHeight(int rows) {
        return rows * CELL_SIZE + (rows - 1) * CELL_GAP;
    }

    public static int visibleRowsFor(int stackCount) {
        int rows = Math.max(1, (stackCount + COLUMNS - 1) / COLUMNS);
        return Math.min(MAX_ROWS, rows);
    }

    /** X of the left edge of cell {@code index}, anchored to the window's right edge. */
    public static float cellX(int index, int screenWidth) {
        float gridLeft = screenWidth - MARGIN_RIGHT - totalWidth(COLUMNS);
        return gridLeft + (index % COLUMNS) * (CELL_SIZE + CELL_GAP);
    }

    /** Y of the bottom edge of cell {@code index} (HUD coords, Y from bottom) — index 0 is top-left. */
    public static float cellY(int index, int rows) {
        int rowFromTop = index / COLUMNS;
        int rowFromBottom = rows - 1 - rowFromTop;
        return MARGIN_BOTTOM + rowFromBottom * (CELL_SIZE + CELL_GAP);
    }

    /**
     * Nonempty stacks in registry order, capped at {@link #MAX_CELLS}. Overflow stays reachable via
     * the Info overlay (I) — this panel is for reaching, not for browsing every zero.
     */
    public static List<ItemType> visibleStacks(Registry<ItemType> items, PlayerInventoryView inventory) {
        List<ItemType> stacks = new ArrayList<>();
        for (ItemType item : items.iterate()) {
            if (inventory.amount(item) > 0) {
                stacks.add(item);
                if (stacks.size() >= MAX_CELLS) {
                    break;
                }
            }
        }
        return List.copyOf(stacks);
    }

    /**
     * Which cell sits under {@code (screenX, screenY)} — {@code Gdx.input} coords (Y from top).
     *
     * @return cell index, or {@code -1} on a miss (including gaps)
     */
    public static int hitTest(float screenX, float screenY, int screenWidth, int screenHeight, int rows) {
        float hudY = screenHeight - screenY;
        int cells = rows * COLUMNS;
        for (int index = 0; index < cells; index++) {
            float x = cellX(index, screenWidth);
            float y = cellY(index, rows);
            if (screenX >= x && screenX <= x + CELL_SIZE && hudY >= y && hudY <= y + CELL_SIZE) {
                return index;
            }
        }
        return -1;
    }

    /** Whether the point lands anywhere on the inventory grid (including empty cells of the shown rows). */
    public static boolean contains(float screenX, float screenY, int screenWidth, int screenHeight, int rows) {
        return hitTest(screenX, screenY, screenWidth, screenHeight, rows) >= 0;
    }

    /** Label drawn above the grid — stays inside the bottom HUD band. */
    public static float labelY(int rows) {
        return Math.min(cellY(0, rows) + CELL_SIZE + 12f, GfxConfig.HUD_BOTTOM_HEIGHT - 4f);
    }
}
