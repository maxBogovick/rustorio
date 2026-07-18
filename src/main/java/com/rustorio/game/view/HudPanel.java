package com.rustorio.game.view;

import java.util.List;

/**
 * Текстовая панель HUD: заголовок и строки, прибитые к углу экрана.
 *
 * <p>Носитель ДАННЫХ, а не рисования: {@code game} собирает панель из чего угодно (статистика,
 * инспекция здания, отладка), а слой {@code render} рисует её обобщённо. Строки — {@link
 * PanelRow}: с иконкой предмета или без. Поэтому новая сводка на экране — это заполнить панель
 * в {@code game}, не открывая {@code render}.
 *
 * @param title  заголовок панели
 * @param rows   строки содержимого ({@link PanelRow})
 * @param corner к какому углу прибита
 */
public record HudPanel(String title, List<PanelRow> rows, Corner corner) {

    public HudPanel {
        rows = List.copyOf(rows);
    }
}
