package com.graphics.render;

import com.graphics.GfxConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Grid} — the one place the model's "row 0 is the TOP of the map" convention meets
 * libGDX's Y-up screen convention. Pure arithmetic, no libGDX types (A1, CODE_REVIEW_2026-07-28.md).
 */
class GridTest {

    private static final float TILE = GfxConfig.TILE;
    private static final int HEIGHT = 10;
    private final Grid grid = new Grid(HEIGHT);

    @Test
    void xIsAStraightColumnMultiple() {
        assertEquals(0f, grid.x(0));
        assertEquals(3 * TILE, grid.x(3));
    }

    @Test
    void yBottomFlipsRowZeroToTheTopOfTheScreen() {
        // Row 0 (the map's TOP row) must land at the HIGHEST screen Y (libGDX draws Y-up);
        // the LAST row must land at screen Y 0 (the very bottom of the drawing area).
        assertEquals((HEIGHT - 1) * TILE, grid.yBottom(0));
        assertEquals(0f, grid.yBottom(HEIGHT - 1));
    }

    @Test
    void centerXAndCenterYAreHalfATileAheadOfXAndYBottom() {
        assertEquals(grid.x(2) + TILE / 2f, grid.centerX(2));
        assertEquals(grid.yBottom(2) + TILE / 2f, grid.centerY(2));
    }

    @Test
    void centerXAndCenterYAcceptFractionalPositionsForCargoMidTile() {
        assertEquals(grid.centerX(2) + TILE / 4f, grid.centerX(2.25f));
    }
}
