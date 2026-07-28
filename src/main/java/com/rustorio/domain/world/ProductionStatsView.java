package com.rustorio.domain.world;

import com.rustorio.domain.Item;
import java.util.List;

/**
 * Read-only face of {@link ProductionStats} — {@code World.stats()} returns this, not {@code
 * ProductionStats} itself, so a caller holding it can read totals but has no way to call {@code
 * ProductionStats#restore} and silently overwrite the running count.
 */
public interface ProductionStatsView {

    long total(Item item);

    ProductionStats.Snapshot snapshot();

    /**
     * Items/minute of {@code item}, averaged over the last {@code windowTicks} of simulated time
     * ending at {@code currentTick} (P-03, DEV_TASKS.md) — ticks, not real time, so the 1×/2×/4×
     * speed multiplier can't distort it.
     */
    double ratePerMinute(Item item, long currentTick, long windowTicks);

    /** One tick-bucket's worth of production — {@link #tick()} is the bucket's START tick, {@link #count()} how many finished within it. */
    record RateSample(long tick, int count) {
    }

    /** {@code item}'s retained rate history, oldest bucket first — what a graph draws (P-03, DEV_TASKS.md). */
    List<RateSample> history(Item item);

    /** How many ticks wide one {@link RateSample} bucket is — the graph's own X-axis unit. */
    long bucketTicks();
}
