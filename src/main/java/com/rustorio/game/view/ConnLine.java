package com.rustorio.game.view;

import com.rustorio.core.Tint;

/**
 * Линия между центрами двух клеток: связь в сети, поток, «это кормит то».
 *
 * <p>Базовый кирпич для логистических/сигнальных сетей и подсказок направления. Координаты — в
 * клетках; render проводит линию между их центрами.
 */
public record ConnLine(int x1, int y1, int x2, int y2, Tint tint) {
}
