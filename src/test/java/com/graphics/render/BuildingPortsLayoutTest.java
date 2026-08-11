package com.graphics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.domain.Direction;
import org.junit.jupiter.api.Test;

/**
 * {@link BuildingPortsLayout} — the arithmetic that makes a 2×2 assembler's OUT sit on the far
 * edge, not on the anchor cell's centre (the bug that made "where do I attach the belt?" unreadable).
 */
class BuildingPortsLayoutTest {

    private static final float TILE = 34f;
    private static final float ANCHOR_X = 100f;
    private static final float ANCHOR_Y = 200f;

    @Test
    void outPortOnATwoByTwoFacingRightSitsNearTheRightEdgeNotPastIt() {
        float x = BuildingPortsLayout.edgeCenterX(ANCHOR_X, TILE, 2, 2, Direction.RIGHT);
        float y = BuildingPortsLayout.edgeCenterY(ANCHOR_Y, TILE, 2, 2, Direction.RIGHT);

        assertEquals(ANCHOR_X + 2 * TILE - BuildingPortsLayout.PORT_INSET_TILES * TILE, x, 0.01f);
        assertEquals(ANCHOR_Y + TILE, y, 0.01f); // mid of 2-tile height
    }

    @Test
    void outArrowTipStaysInsideTheFootprint() {
        // Live bug: OUT tip used to sit in the neighbour cell and paint over its building sprite.
        assertTrue(BuildingPortsLayout.OUT_REACH_TILES <= BuildingPortsLayout.PORT_INSET_TILES);
        float rightEdge = ANCHOR_X + TILE;
        float cx = BuildingPortsLayout.edgeCenterX(ANCHOR_X, TILE, 1, 1, Direction.RIGHT);
        float tipX = cx + TILE * BuildingPortsLayout.OUT_REACH_TILES;
        assertTrue(tipX <= rightEdge + 0.01f);
        assertTrue(tipX > ANCHOR_X);
    }

    @Test
    void inPortOnTheLeftEdgePointsInwardToTheRight() {
        assertEquals(Direction.RIGHT, BuildingPortsLayout.inward(Direction.LEFT));
    }

    @Test
    void onlyPrimaryAndSecondaryCountAsOutputSides() {
        assertTrue(BuildingPortsLayout.isOutputSide(Direction.RIGHT, Direction.RIGHT, null));
        assertFalse(BuildingPortsLayout.isOutputSide(Direction.LEFT, Direction.RIGHT, null));
        assertTrue(BuildingPortsLayout.isOutputSide(Direction.DOWN, Direction.RIGHT, Direction.DOWN));
    }

    @Test
    void oneByOneOutPortIsInsetFromTheOuterEdge() {
        float x = BuildingPortsLayout.edgeCenterX(ANCHOR_X, TILE, 1, 1, Direction.RIGHT);
        assertEquals(ANCHOR_X + TILE - BuildingPortsLayout.PORT_INSET_TILES * TILE, x, 0.01f);
        assertEquals(ANCHOR_X + TILE / 2f, BuildingPortsLayout.edgeCenterX(ANCHOR_X, TILE, 1, 1, Direction.UP),
                0.01f);
    }

    @Test
    void showsInputPortsOnlyForMultiCellFootprint() {
        assertFalse(BuildingPortsLayout.showsInputPorts(1, 1));
        assertTrue(BuildingPortsLayout.showsInputPorts(2, 2));
        assertTrue(BuildingPortsLayout.showsInputPorts(2, 1));
    }
}
