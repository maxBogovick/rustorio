package com.rustorio.domain.world;

import com.rustorio.domain.Item;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * How many of each item have been produced since the game started — the factory's running total,
 * not any one building's. A building could keep its own count (a chest already counts what it
 * holds), but demolishing it would erase that history; this lives in the world instead, outliving
 * any single building (see {@link World#stats()}).
 *
 * <p><b>Rate history (P-03, DEV_TASKS.md).</b> A lifetime total is useless for judging whether a
 * production line is actually keeping up right now — the design audit's own point (§4.1): the
 * genre's real metric is items/minute, not "produced 4000 since the game started." {@link
 * #onProduced} additionally buckets each event by {@link #BUCKET_TICKS}-tick window (using the
 * tick D-06 added to this call, never wall-clock time — see {@link ProductionListener}'s own
 * javadoc for why ticks are the only axis a 1×/2×/4× speed multiplier and pause don't distort) into
 * a ring buffer capped at {@link #MAX_BUCKETS}: old buckets are dropped as new ones arrive, so this
 * stays bounded regardless of how long the game runs (the card's own risk note).
 */
public final class ProductionStats implements ProductionListener, ProductionStatsView {

    /** One bucket's width in ticks — 60 ticks is 1 second of simulated time at the base (1×, unpaused) clock. */
    static final long BUCKET_TICKS = 60;
    /** Ring buffer depth — {@link #BUCKET_TICKS} × this many = 10 simulated minutes of retained history, then oldest buckets drop. */
    private static final int MAX_BUCKETS = 600;

    private final Map<Item, Long> totals = new EnumMap<>(Item.class);
    private final Map<Item, Deque<Bucket>> history = new EnumMap<>(Item.class);

    /** One item's count within one {@link #BUCKET_TICKS}-wide tick window — mutable so a same-bucket event just increments it in place, not a new allocation per production event. */
    private static final class Bucket {
        final long index;
        int count;

        Bucket(long index) {
            this.index = index;
            this.count = 1;
        }
    }

    @Override
    public void onProduced(long tick, Item item) {
        totals.merge(item, 1L, Long::sum);

        Deque<Bucket> deque = history.computeIfAbsent(item, unused -> new ArrayDeque<>());
        long bucketIndex = tick / BUCKET_TICKS;
        Bucket last = deque.peekLast();
        if (last != null && last.index == bucketIndex) {
            last.count++;
        } else {
            deque.addLast(new Bucket(bucketIndex));
            while (deque.size() > MAX_BUCKETS) {
                deque.removeFirst();
            }
        }
    }

    @Override
    public long total(Item item) {
        return totals.getOrDefault(item, 0L);
    }

    /**
     * Items/minute of {@code item}, averaged over the last {@code windowTicks} of SIMULATED time
     * ending at {@code currentTick} — not real time, so the speed multiplier can't distort it (the
     * card's own acceptance criterion). {@code 0} if nothing in that window, never negative, never
     * a division by a zero-tick window (callers are expected to pass a positive window; this
     * doesn't defend that precondition per this codebase's own "don't guard the impossible" rule).
     *
     * <p>Walks {@code deque} NEWEST first (code review finding) and stops the moment a bucket falls
     * before {@code cutoff}: {@link #onProduced} only ever appends, so {@code bucket.index} is
     * strictly increasing front-to-back — once one bucket, walking backward from the newest end, is
     * too old for the window, every earlier one is too. Before this, the loop always scanned every
     * RETAINED bucket (up to {@link #MAX_BUCKETS} = 600, i.e. ten simulated minutes of history)
     * even when {@code windowTicks} asked for a tiny fraction of that — the requested window, not
     * the full retention period, now bounds the work. (A tempting alternative — a running sum kept
     * up to date in {@link #onProduced} — doesn't actually work here: {@code windowTicks} is a
     * per-call parameter, not a fixed constant, so a single running total can't answer for an
     * arbitrary window without becoming wrong for every window narrower than the full history.)
     */
    @Override
    public double ratePerMinute(Item item, long currentTick, long windowTicks) {
        long cutoff = currentTick - windowTicks;
        long count = 0;
        Deque<Bucket> deque = history.get(item);
        if (deque != null) {
            Iterator<Bucket> newestFirst = deque.descendingIterator();
            while (newestFirst.hasNext()) {
                Bucket bucket = newestFirst.next();
                long bucketStartTick = bucket.index * BUCKET_TICKS;
                if (bucketStartTick < cutoff) {
                    break; // this and every bucket before it (older, since we're walking backward) are outside the window
                }
                if (bucketStartTick <= currentTick) {
                    count += bucket.count;
                }
            }
        }
        double minutesInWindow = windowTicks / 60.0 / 60.0; // ticks -> simulated seconds -> simulated minutes
        return count / minutesInWindow;
    }

    /**
     * The retained per-bucket history for {@code item}, oldest first — what a graph draws. Each
     * {@link RateSample#tick()} is that bucket's START tick, {@link RateSample#count()} how many
     * finished within it (raw count, not yet per-minute — the caller decides how to scale/label an
     * axis; {@link #BUCKET_TICKS} is public via {@link #bucketTicks()} for exactly that).
     */
    @Override
    public List<RateSample> history(Item item) {
        Deque<Bucket> deque = history.get(item);
        if (deque == null) {
            return List.of();
        }
        return deque.stream()
                .map(bucket -> new RateSample(bucket.index * BUCKET_TICKS, bucket.count))
                .toList();
    }

    @Override
    public long bucketTicks() {
        return BUCKET_TICKS;
    }

    void clear() {
        totals.clear();
        history.clear();
    }

    /**
     * Immutable point-in-time snapshot for persistence (Memento pattern). Lifetime totals only —
     * the rate history is deliberately NOT persisted: it's a live "how's the factory doing right
     * now" instrument, not save-worthy state, the same call already made for {@code BuildingStatus}
     * (F-01) and a furnace's ephemeral status field.
     */
    public record Snapshot(Map<Item, Long> totals) {
        public Snapshot {
            totals = Map.copyOf(totals);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(totals); // Snapshot's compact constructor already copies (R2, CODE_REVIEW_2026-07-28.md)
    }

    /** Package-private: only {@link World#restoreStats} (same package) may overwrite totals wholesale. */
    void restore(Snapshot snapshot) {
        clear();
        totals.putAll(snapshot.totals());
    }
}
