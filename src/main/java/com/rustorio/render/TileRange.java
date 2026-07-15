package com.rustorio.render;

/**
 * Прямоугольник клеток, видимых камерой (границы включительно).
 * Если камера целиком за полем, диапазон пуст ({@code maxX < minX}) — циклы
 * отрисовки по нему просто не сделают ни одной итерации.
 */
public record TileRange(int minX, int minY, int maxX, int maxY) {
}
