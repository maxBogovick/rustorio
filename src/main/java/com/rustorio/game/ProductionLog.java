package com.rustorio.game;

import com.rustorio.core.Item;
import com.rustorio.core.ProductionObserver;

import java.util.EnumMap;
import java.util.Map;

/**
 * Второй, независимый наблюдатель: печатает веху каждый раз, когда суммарное производство
 * предмета переходит очередную сотню.
 *
 * <p>Существует, чтобы показать главное в шаблоне Observer: подписчиков МНОГО, и они не знают
 * друг о друге. {@link ProductionStats} копит числа, этот — логирует; издатель (машина) шлёт
 * одно событие, а реакций на него столько, сколько подписчиков.
 */
public final class ProductionLog implements ProductionObserver {

    private static final int STEP = 100;

    private final Map<Item, Long> running = new EnumMap<>(Item.class);

    @Override
    public void onProduced(Item item, int amount) {
        long prev = running.getOrDefault(item, 0L);
        long now = prev + amount;
        running.put(item, now);
        if (now / STEP != prev / STEP) {
            System.out.println("Производство: " + item + " ×" + (now / STEP * STEP));
        }
    }
}
