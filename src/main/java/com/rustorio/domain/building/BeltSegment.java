package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A straight belt run as one entity: an ordered list of tiles (tail = entry, head = exit) that all
 * advance together in a single {@link #tick} call instead of N independent {@code Belt#tick}
 * calls each separately calling {@code world.offerForward} one tile ahead.
 *
 * <p>Deliberately O(length) per tick, not O(1): a real transport-belt engine moves cargo via one
 * shared offset instead of walking every item. That would be premature here — belts on this map
 * are short, and a plain scan is easier to verify with merge/split tests, which is the riskiest
 * part of this class. Upgrading to O(1) is a future milestone, gated on a benchmark actually
 * showing the need (see {@code Benchmark}), not on intuition.
 */
final class BeltSegment {

    private final Direction direction;

    /** Index 0 is the tail (entry point); the last element is the head (exit point). */
    private final SequencedCollection<Belt> tiles = new ArrayDeque<>();

    /**
     * Mirrors {@link #tiles}'s membership exactly, kept in sync by every method that touches
     * {@code tiles} — exists purely so {@link #requireNotAlreadyPresent} is O(1), not O(length)
     * (code review finding, OPT-03): {@code ArrayDeque#contains} is a linear scan, so building a
     * long belt run tile-by-tile (each placement calling {@link #addHead}/{@link #addTail}) used to
     * cost O(1+2+...+N) = O(N²) total, purely for this membership check — the {@link #tick} loop
     * itself was never the problem (see that method's own javadoc on why it stays O(length)).
     */
    private final Set<Belt> membership = new HashSet<>();

    BeltSegment(Direction direction) {
        this.direction = direction;
    }

    Direction direction() {
        return direction;
    }

    int size() {
        return tiles.size();
    }

    /** Only the tail drives the segment's tick — see {@link Belt#tick}. */
    boolean isTail(Belt belt) {
        return tiles.getFirst() == belt;
    }

    /** Attach {@code belt} as a new head (built directly past the current head). */
    void addHead(Belt belt) {
        requireNotAlreadyPresent(belt);
        tiles.addLast(belt);
        membership.add(belt);
        belt.joinSegment(this);
    }

    /** Attach {@code belt} as a new tail (built directly before the current tail). */
    void addTail(Belt belt) {
        requireNotAlreadyPresent(belt);
        tiles.addFirst(belt);
        membership.add(belt);
        belt.joinSegment(this);
    }

    /**
     * Callers must leave whatever segment a belt is currently in before re-attaching it — see
     * {@code World#restoreBuilding}. A tile appearing twice in {@link #tiles} would silently
     * corrupt {@link #tick} and {@link #size}; fail loudly instead.
     */
    private void requireNotAlreadyPresent(Belt belt) {
        if (membership.contains(belt)) {
            throw new IllegalStateException("belt is already in this segment");
        }
    }

    /** Merge {@code other} (immediately past this segment's head, same direction) into this one. */
    void mergeHead(BeltSegment other) {
        for (Belt belt : other.tiles) {
            tiles.addLast(belt);
            membership.add(belt);
            belt.joinSegment(this);
        }
    }

    /**
     * Remove {@code belt} from the segment (a building was demolished). Shrinks from an edge, or —
     * if the tile was in the middle — splits into two independent segments around the hole. Cargo
     * the removed tile was holding leaves with it, never duplicated onto a neighbor.
     */
    void remove(Belt belt) {
        List<Belt> ordered = new ArrayList<>(tiles);
        int index = ordered.indexOf(belt);
        tiles.clear();
        membership.clear();

        for (int i = 0; i < index; i++) {
            tiles.addLast(ordered.get(i));
            membership.add(ordered.get(i));
        }
        if (index < ordered.size() - 1) {
            BeltSegment tail = new BeltSegment(direction);
            for (int i = index + 1; i < ordered.size(); i++) {
                tail.addHead(ordered.get(i));
            }
        }
        belt.joinSegment(null);
    }

    /**
     * Live one tick: the head tries to exit via {@code tryExit}; if it leaves, the next tile
     * toward the tail advances into its place, and so on down the chain. One head-to-tail pass
     * gives the "no more than one tile per tick" guarantee WITHIN this one segment.
     *
     * <p>A tile whose {@link Belt#arrivedThisTick} is set is skipped as a cargo SOURCE (it can
     * still be a valid destination — {@code next.held() == null} still checks its actual state):
     * it only just received that cargo via {@code accept}, earlier in the SAME world tick, from a
     * different segment or building entirely. Moving it again in this same pass is exactly the
     * cross-segment double-move P2-07 found; {@code TickScheduler} clears the mark once per frame,
     * before either traversal pass runs, so a tile is only ever blocked for the remainder of the
     * tick it arrived in — see P3-03, BUG_FIX_PROGRESS.md.
     */
    void tick(Predicate<ItemType> tryExit) {
        Belt next = null;
        var it = tiles.reversed().iterator();
        while (it.hasNext()) {
            Belt belt = it.next();
            boolean eligibleSource = !belt.arrivedThisTick();
            if (next == null) {
                ItemType head = belt.held();
                if (eligibleSource && head != null && tryExit.test(head)) {
                    belt.clearHeld();
                }
            } else if (eligibleSource && belt.held() != null && next.held() == null) {
                next.setHeld(belt.held());
                belt.clearHeld();
            }
            next = belt;
        }
    }
}
