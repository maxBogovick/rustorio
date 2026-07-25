package com.rustorio.domain.world;

import com.rustorio.domain.Item;

/**
 * Read-only face of {@link ProductionStats} — {@code World.stats()} returns this, not {@code
 * ProductionStats} itself, so a caller holding it can read totals but has no way to call {@code
 * ProductionStats#restore} and silently overwrite the running count.
 */
public interface ProductionStatsView {

    long total(Item item);

    ProductionStats.Snapshot snapshot();
}
