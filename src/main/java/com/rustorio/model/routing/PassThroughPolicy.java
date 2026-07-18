package com.rustorio.model.routing;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;

/**
 * Простейшая политика: любой предмет — прямо.
 *
 * <p>Это «базовая линия» семейства: сортировщик с ней ведёт себя как обычная лента. Ценна
 * именно как самый простой представитель — на её фоне видно, что делают остальные политики,
 * и она доказывает, что интерфейс {@link RoutingPolicy} не тащит лишнего: правилу «всё
 * прямо» не нужны ни тип предмета, ни боковые выходы.
 */
public final class PassThroughPolicy implements RoutingPolicy {

    @Override
    public Direction route(Item item, Direction front, Direction right, Direction left) {
        return front;
    }

    @Override
    public String name() {
        return "pass";
    }
}
