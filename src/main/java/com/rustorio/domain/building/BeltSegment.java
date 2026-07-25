package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;
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
        tiles.addLast(belt);
        belt.joinSegment(this);
    }

    /** Attach {@code belt} as a new tail (built directly before the current tail). */
    void addTail(Belt belt) {
        tiles.addFirst(belt);
        belt.joinSegment(this);
    }

    /** Merge {@code other} (immediately past this segment's head, same direction) into this one. */
    void mergeHead(BeltSegment other) {
        for (Belt belt : other.tiles) {
            tiles.addLast(belt);
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

        for (int i = 0; i < index; i++) {
            tiles.addLast(ordered.get(i));
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
     * toward the tail advances into its place, and so on down the chain. One head-to-tail pass is
     * the same "no more than one tile per tick" guarantee the old two-phase world traversal gave —
     * no longer needed BETWEEN segments here, only within one.
     */
    void tick(Predicate<Item> tryExit) {
        Belt next = null;
        var it = tiles.reversed().iterator();
        while (it.hasNext()) {
            Belt belt = it.next();
            if (next == null) {
                Item head = belt.held();
                if (head != null && tryExit.test(head)) {
                    belt.clearHeld();
                }
            } else if (belt.held() != null && next.held() == null) {
                next.setHeld(belt.held());
                belt.clearHeld();
            }
            next = belt;
        }
    }
}
