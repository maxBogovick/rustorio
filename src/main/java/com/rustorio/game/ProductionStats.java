package com.rustorio.game;

import com.rustorio.core.Item;
import com.rustorio.core.ProductionObserver;

import java.util.EnumMap;
import java.util.Map;

/**
 * Наблюдатель, который копит суммарное производство по каждому предмету.
 *
 * <p>Ни одна машина не знает про этот счётчик: он подписан на {@link
 * com.rustorio.core.ProductionBus} и получает события. Захотим второй счётчик (за смену, за
 * минуту) — заведём ещё одного наблюдателя, машины не тронем.
 */
public final class ProductionStats implements ProductionObserver {

    private final Map<Item, Long> totals = new EnumMap<>(Item.class);

    @Override
    public void onProduced(Item item, int amount) {
        totals.merge(item, (long) amount, Long::sum);
    }

    /** Сколько всего произведено предмета за игру. */
    public long total(Item item) {
        return totals.getOrDefault(item, 0L);
    }

    /** Копия всех счётчиков (для интерфейса или отладки). */
    public Map<Item, Long> snapshot() {
        return new EnumMap<>(totals);
    }
}
