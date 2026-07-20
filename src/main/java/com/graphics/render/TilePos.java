package com.graphics.render;

/**
 * Клетка поля под курсором (в координатах поля). Может указывать и за край поля —
 * проверку {@code inBounds} делает вызывающий (мир).
 */
public record TilePos(int x, int y) {
}
