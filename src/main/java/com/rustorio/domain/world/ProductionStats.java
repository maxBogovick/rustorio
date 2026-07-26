package com.rustorio.domain.world;

import com.rustorio.domain.Item;
import java.util.EnumMap;
import java.util.Map;

/**
 * How many of each item have been produced since the game started — the factory's running total,
 * not any one building's. A building could keep its own count (a chest already counts what it
 * holds), but demolishing it would erase that history; this lives in the world instead, outliving
 * any single building (see {@link World#stats()}).
 */
public final class ProductionStats implements ProductionListener, ProductionStatsView {

    private final Map<Item, Long> totals = new EnumMap<>(Item.class);

    @Override
    public void onProduced(Item item) {
        totals.merge(item, 1L, Long::sum);
    }

    @Override
    public long total(Item item) {
        return totals.getOrDefault(item, 0L);
    }

    void clear() {
        totals.clear();
    }

    /** Immutable point-in-time snapshot for persistence (Memento pattern). */
    public record Snapshot(Map<Item, Long> totals) {
        public Snapshot {
            totals = Map.copyOf(totals);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(Map.copyOf(totals));
    }

    /** Package-private: only {@link World#restoreStats} (same package) may overwrite totals wholesale. */
    void restore(Snapshot snapshot) {
        clear();
        totals.putAll(snapshot.totals());
    }
}
