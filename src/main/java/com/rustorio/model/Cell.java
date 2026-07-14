package com.rustorio.model;

/**
 * Координата клетки поля {@code (x, y)}.
 *
 * <p>Отдельный {@code record} вместо «двух голых int» делает сигнатуры
 * самодокументируемыми (например {@code Optional<Cell> hover()}) и не даёт
 * перепутать местами x и y. Домен принципиально не зависит от libGDX, поэтому
 * своя {@code Cell}, а не {@code GridPoint2}.
 */
public record Cell(int x, int y) {
}
