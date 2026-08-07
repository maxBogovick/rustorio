package com.graphics.render;

/**
 * Where each cell of the quick bar sits on screen — the same one-formula-two-consumers arrangement
 * {@link HotbarLayout} has, and for the same reason: drawing ({@link HudRenderer}) and hit-testing
 * ({@code com.graphics.input.InputHandler}, another package) must never compute it twice.
 *
 * <p>Anchored to the BOTTOM-LEFT corner, and that choice is what makes the grid free: a bar centred
 * on the screen has to move every time it grows a row, so a cell the player learned the position of
 * would slide out from under the cursor. Growing upward from a fixed corner leaves rows one and two
 * exactly where they were.
 *
 * <p>Cells are smaller than a {@link HotbarLayout#SLOT_SIZE} slot because they carry no label —
 * icons only, by the owner's decision, so the caption strip that forced the old slot's height is
 * simply not there.
 */
public final class QuickBarLayout {

    /** Side of one cell in screen pixels. */
    public static final float CELL_SIZE = 44f;
    /** Gap between neighbouring cells, both directions. */
    public static final float CELL_GAP = 4f;
    /** Distance from the window's left edge to the grid. */
    public static final float MARGIN_LEFT = 14f;
    /** Distance from the window's bottom edge to the grid's bottom row. */
    public static final float MARGIN_BOTTOM = 14f;

    private QuickBarLayout() {
    }

    /** Total width of a grid {@code columns} wide — constant, since the grid grows in rows rather than columns. */
    public static float totalWidth(int columns) {
        return columns * CELL_SIZE + (columns - 1) * CELL_GAP;
    }

    /** Total height of a grid {@code rows} tall. */
    public static float totalHeight(int rows) {
        return rows * CELL_SIZE + (rows - 1) * CELL_GAP;
    }

    /** X of the left edge of the cell at {@code index}, laid out left to right within its row. */
    public static float cellX(int index, int columns) {
        return MARGIN_LEFT + (index % columns) * (CELL_SIZE + CELL_GAP);
    }

    /**
     * Y of the bottom edge of the cell at {@code index}, in HUD coordinates (Y from the bottom).
     *
     * <p>Index 0 is the TOP-left cell, so reading order matches keys 1-9 the way a numeric keypad
     * does — while the grid itself still grows upward from the fixed bottom-left corner. Those two
     * facts pull in opposite directions, which is precisely why this arithmetic lives in one tested
     * place instead of inline in the renderer.
     */
    public static float cellY(int index, int columns, int rows) {
        int rowFromTop = index / columns;
        int rowFromBottom = rows - 1 - rowFromTop;
        return MARGIN_BOTTOM + rowFromBottom * (CELL_SIZE + CELL_GAP);
    }

    /**
     * Which cell sits under {@code (screenX, screenY)} — {@code Gdx.input}'s screen coordinates
     * (Y from the TOP), flipped here so no caller has to remember the difference.
     *
     * @return the cell index, or {@code -1} when the point missed the grid (including the gaps
     *     between cells, which belong to the world behind, not to a neighbour)
     */
    public static int hitTest(float screenX, float screenY, int screenHeight, int columns, int rows) {
        float hudY = screenHeight - screenY;
        for (int index = 0; index < columns * rows; index++) {
            float x = cellX(index, columns);
            float y = cellY(index, columns, rows);
            if (screenX >= x && screenX <= x + CELL_SIZE && hudY >= y && hudY <= y + CELL_SIZE) {
                return index;
            }
        }
        return -1;
    }
}
