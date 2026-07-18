package com.rustorio.game.view;

import com.rustorio.core.Tint;

/**
 * Подсветка одной клетки поля заданным смыслом-цветом.
 *
 * <p>Базовый кирпич для выделения, показа радиуса действия, превью чертежа, тепловых карт:
 * {@code game} кладёт набор таких пометок, {@code render} закрашивает клетки. Координаты — в
 * клетках поля; перевод в пиксели и цвет — забота {@code render}.
 */
public record TileHighlight(int x, int y, Tint tint) {
}
