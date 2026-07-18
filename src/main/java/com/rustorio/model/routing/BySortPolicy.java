package com.rustorio.model.routing;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;

/**
 * Сортировка по типу предмета: каждому виду — свой из трёх выходов.
 *
 * <p>Ради этой политики сортировщик и затевался. Выход выбирается по номеру предмета в
 * перечислении {@link Item} по остатку от деления на три: одинаковый предмет всегда уедет в
 * одну и ту же сторону (детерминированно), а разные — разъедутся по разным лентам. Это и
 * есть «суши-лента» из игр жанра: один вход, на выходах предметы уже разложены по сортам.
 *
 * <p><b>Важно:</b> если нужный выход занят, предмет ЖДЁТ — он не «протечёт» в чужую ленту.
 * Так и должно быть у сортировщика: смысл в том, чтобы руда не оказалась в ленте для
 * шестерёнок только потому, что её лента забита. Ожидание обеспечивает не политика (она лишь
 * называет направление), а обычная фаза передачи: занятый выход — предмет остаётся в здании.
 */
public final class BySortPolicy implements RoutingPolicy {

    @Override
    public Direction route(Item item, Direction front, Direction right, Direction left) {
        return switch (item.ordinal() % 3) {
            case 0 -> front;
            case 1 -> right;
            default -> left;
        };
    }

    @Override
    public String name() {
        return "sort";
    }
}
