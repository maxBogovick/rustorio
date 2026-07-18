package com.rustorio.game.view;

import com.rustorio.core.Tint;

/**
 * Текст, привязанный к клетке поля (плавающая метка над зданием): алерт «нет сырья», имя,
 * число. В отличие от {@link HudPanel} (прибита к углу окна), метка живёт в мировых
 * координатах и едет вместе с картой при скролле/зуме. Перевод в пиксели и цвет — забота render.
 */
public record WorldLabel(int x, int y, String text, Tint tint) {
}
