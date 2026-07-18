package com.rustorio.game.view;

import com.rustorio.core.Item;
import org.jspecify.annotations.Nullable;

/**
 * Строка HUD-панели: текст и, по желанию, иконка предмета слева от него.
 *
 * <p>Одна модель на два случая: обычная текстовая строка ({@link #text}) и строка-легенда с
 * иконкой предмета ({@link #of}). Render рисует иконку по спрайту предмета, если она задана.
 *
 * @param icon предмет-иконка слева ({@code null} — без иконки)
 * @param text текст строки
 */
public record PanelRow(@Nullable Item icon, String text) {

    /** Строка без иконки. */
    public static PanelRow text(String text) {
        return new PanelRow(null, text);
    }

    /** Строка с иконкой предмета. */
    public static PanelRow of(Item icon, String text) {
        return new PanelRow(icon, text);
    }
}
