package com.graphics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link QuickBarLayout} — geometry only, no GL context, same split {@link CameraViewportTest} and
 * {@link HotbarLayoutTest} already follow. Worth its own tests because two of its rules point in
 * opposite directions: the grid grows UPWARD from a fixed corner, while cell 0 is the TOP-left one
 * so that keys 1-9 read like a keypad.
 */
class QuickBarLayoutTest {

    private static final int COLUMNS = 3;
    private static final int SCREEN_HEIGHT = 800;

    @Test
    void theBottomRowStaysPutWhenTheGridGrows() {
        // Cell 0 of a one-row grid, and cell 6 of a three-row grid, are both bottom-left.
        float oneRowBottomLeft = QuickBarLayout.cellY(0, COLUMNS, 1);
        float threeRowBottomLeft = QuickBarLayout.cellY(6, COLUMNS, 3);

        assertEquals(QuickBarLayout.MARGIN_BOTTOM, oneRowBottomLeft,
                "a single row sits on the bottom margin");
        assertEquals(oneRowBottomLeft, threeRowBottomLeft,
                "growing to three rows must not move the bottom row — a learned cell position "
                        + "that slides out from under the cursor is worse than no grid at all");
    }

    @Test
    void cellZeroIsTopLeftSoTheNumbersReadLikeAKeypad() {
        float topRow = QuickBarLayout.cellY(0, COLUMNS, 3);
        float middleRow = QuickBarLayout.cellY(3, COLUMNS, 3);
        float bottomRow = QuickBarLayout.cellY(6, COLUMNS, 3);

        assertTrue(topRow > middleRow, "cell 0 is above cell 3");
        assertTrue(middleRow > bottomRow, "cell 3 is above cell 6");
        assertEquals(QuickBarLayout.cellX(0, COLUMNS), QuickBarLayout.cellX(3, COLUMNS),
                "cells 0, 3 and 6 share a column");
    }

    @Test
    void columnsRunLeftToRightFromTheLeftMargin() {
        assertEquals(QuickBarLayout.MARGIN_LEFT, QuickBarLayout.cellX(0, COLUMNS));
        assertEquals(QuickBarLayout.MARGIN_LEFT + QuickBarLayout.CELL_SIZE + QuickBarLayout.CELL_GAP,
                QuickBarLayout.cellX(1, COLUMNS));
    }

    @Test
    void hitTestFindsTheCellUnderThePointAndTheGapsBelongToNobody() {
        int rows = 2;
        // Middle of cell 0 (top-left), converted from HUD Y back to Gdx screen Y.
        float hudY = QuickBarLayout.cellY(0, COLUMNS, rows) + QuickBarLayout.CELL_SIZE / 2f;
        float screenY = SCREEN_HEIGHT - hudY;
        float screenX = QuickBarLayout.cellX(0, COLUMNS) + QuickBarLayout.CELL_SIZE / 2f;

        assertEquals(0, QuickBarLayout.hitTest(screenX, screenY, SCREEN_HEIGHT, COLUMNS, rows));

        // Straight into the gap between columns 0 and 1 — must not round to either neighbour.
        float gapX = QuickBarLayout.cellX(0, COLUMNS) + QuickBarLayout.CELL_SIZE + QuickBarLayout.CELL_GAP / 2f;
        assertEquals(-1, QuickBarLayout.hitTest(gapX, screenY, SCREEN_HEIGHT, COLUMNS, rows),
                "a click in the gap belongs to the world behind the grid, not to a cell");
    }

    @Test
    void aClickWellAwayFromTheCornerMissesEntirely() {
        assertEquals(-1, QuickBarLayout.hitTest(900f, 100f, SCREEN_HEIGHT, COLUMNS, 3));
        assertEquals(-1, QuickBarLayout.hitTest(20f, 20f, SCREEN_HEIGHT, COLUMNS, 3),
                "near the TOP-left of the window is not the grid — it lives at the bottom");
    }
}
