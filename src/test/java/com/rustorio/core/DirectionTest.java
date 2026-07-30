package com.rustorio.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Направление — чистые данные: проверяется без окна и движка. */
class DirectionTest {

    @Test
    void rotateCwGoesClockwise() {
        assertEquals(Direction.EAST, Direction.NORTH.rotateCw());
        assertEquals(Direction.SOUTH, Direction.EAST.rotateCw());
        assertEquals(Direction.WEST, Direction.SOUTH.rotateCw());
        assertEquals(Direction.NORTH, Direction.WEST.rotateCw());
    }

    @Test
    void offsetsPointToNeighbours() {
        // Ось Y растёт ВНИЗ (строки поля считаются сверху) — поэтому North = -1.
        assertEquals(0, Direction.NORTH.dx());
        assertEquals(-1, Direction.NORTH.dy());
        assertEquals(1, Direction.EAST.dx());
        assertEquals(0, Direction.EAST.dy());
        assertEquals(0, Direction.SOUTH.dx());
        assertEquals(1, Direction.SOUTH.dy());
        assertEquals(-1, Direction.WEST.dx());
        assertEquals(0, Direction.WEST.dy());
    }
}
