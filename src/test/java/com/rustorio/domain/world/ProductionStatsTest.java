package com.rustorio.domain.world;

import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProductionStats}: lifetime totals (unchanged) plus the P-03 (DEV_TASKS.md) tick-bucketed
 * rate history a stats screen graphs — items/minute, and the ring buffer that backs it staying
 * bounded regardless of how long a game session runs (the card's own risk note).
 */
class ProductionStatsTest {

    @Test
    void totalAccumulatesAcrossTicks() {
        ProductionStats stats = new ProductionStats();
        stats.onProduced(1, VanillaItems.IRON_ORE);
        stats.onProduced(5, VanillaItems.IRON_ORE);
        stats.onProduced(5, VanillaItems.GEAR);

        assertEquals(2, stats.total(VanillaItems.IRON_ORE));
        assertEquals(1, stats.total(VanillaItems.GEAR));
        assertEquals(0, stats.total(VanillaItems.COAL));
    }

    /** 6 units inside a 60-tick (1 simulated second) window extrapolates to 360/min. */
    @Test
    void ratePerMinuteExtrapolatesFromTheCountInTheWindow() {
        ProductionStats stats = new ProductionStats();
        for (int i = 0; i < 6; i++) {
            stats.onProduced(i, VanillaItems.IRON_ORE);
        }

        double rate = stats.ratePerMinute(VanillaItems.IRON_ORE, 60, 60);

        assertEquals(360.0, rate, 0.001);
    }

    @Test
    void ratePerMinuteIsZeroOutsideTheWindow() {
        ProductionStats stats = new ProductionStats();
        stats.onProduced(0, VanillaItems.IRON_ORE); // long, long ago

        double rate = stats.ratePerMinute(VanillaItems.IRON_ORE, 100_000, 60);

        assertEquals(0.0, rate);
    }

    /** (Live design goal) The metric must not care HOW FAST real time passed — only the tick axis, so the 1×/2×/4× multiplier can't distort it. */
    @Test
    void ratePerMinuteIsUnaffectedByHowManyRealSecondsThoseTicksTook() {
        ProductionStats fast = new ProductionStats();
        ProductionStats slow = new ProductionStats();
        for (int i = 0; i < 6; i++) {
            fast.onProduced(i, VanillaItems.IRON_ORE); // same 6 ticks either way — the point is the axis is ticks, not wall time
            slow.onProduced(i, VanillaItems.IRON_ORE);
        }

        assertEquals(fast.ratePerMinute(VanillaItems.IRON_ORE, 60, 60), slow.ratePerMinute(VanillaItems.IRON_ORE, 60, 60));
    }

    @Test
    void historyBucketsEventsByTickWindow() {
        ProductionStats stats = new ProductionStats();
        stats.onProduced(0, VanillaItems.IRON_ORE);
        stats.onProduced(10, VanillaItems.IRON_ORE); // same bucket (ticks 0-59)
        stats.onProduced(60, VanillaItems.IRON_ORE); // next bucket

        List<ProductionStatsView.RateSample> history = stats.history(VanillaItems.IRON_ORE);

        assertEquals(2, history.size());
        assertEquals(0L, history.get(0).tick());
        assertEquals(2, history.get(0).count());
        assertEquals(60L, history.get(1).tick());
        assertEquals(1, history.get(1).count());
    }

    @Test
    void historyIsBoundedRegardlessOfHowLongTheGameRuns() {
        ProductionStats stats = new ProductionStats();
        // Far more buckets than the retention window — one event per bucket, for a LOT of buckets.
        for (int bucket = 0; bucket < 10_000; bucket++) {
            stats.onProduced(bucket * ProductionStats.BUCKET_TICKS, VanillaItems.IRON_ORE);
        }

        assertTrue(stats.history(VanillaItems.IRON_ORE).size() <= 600,
                "the ring buffer must stay bounded — see the card's own risk note");
    }

    @Test
    void snapshotAndRestoreRoundTripTotalsOnly() {
        ProductionStats stats = new ProductionStats();
        stats.onProduced(1, VanillaItems.IRON_ORE);
        stats.onProduced(2, VanillaItems.IRON_ORE);

        ProductionStats.Snapshot snapshot = stats.snapshot();
        ProductionStats restored = new ProductionStats();
        restored.restore(snapshot);

        assertEquals(2, restored.total(VanillaItems.IRON_ORE));
    }
}
