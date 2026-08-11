package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
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
 * <p>Holds {@link TransportNode} tiles, not specifically {@link Belt}: any class implementing that
 * capability interface can join this cascade on equal footing with a vanilla {@link Belt} — this is
 * the whole point of the interface, not an incidental generalization. {@link UndergroundBelt} is
 * deliberately NOT one of those tiles; it never joins a segment at all (see {@link
 * UndergroundBelt#findPartner}).
 *
 * <p>Deliberately O(length) per tick, not O(1): a real transport-belt engine moves cargo via one
 * shared offset instead of walking every item. That would be premature here — belts on this map
 * are short, and a plain scan is easier to verify with merge/split tests, which is the riskiest
 * part of this class. Upgrading to O(1) is a future milestone, gated on a benchmark actually
 * showing the need (see {@code Benchmark}), not on intuition.
 *
 * <p>Public since the phase's own acceptance capstone: {@link TransportNode#segment()}/{@link
 * TransportNode#joinSegment} return/accept this type, so a mod's own {@link TransportNode}
 * implementer — living outside this package — needs to be able to name it in its own method
 * signatures, or it can't implement the interface at all. Every METHOD here stays package-private
 * on purpose: a foreign implementer never calls into a segment directly, only through {@link
 * TransportNode#attachToNeighbors}'s default method (compiled in this package, so it can still
 * call {@link #addHead}/{@link #addTail}/etc. regardless of the tile's actual runtime class) and
 * {@code BuildingFactory}'s own narrow doors — the class needed to be nameable, not its internals
 * touchable.
 */
public final class BeltSegment {

    private final Direction direction;

    /** Index 0 is the tail (entry point); the last element is the head (exit point). */
    private final SequencedCollection<TransportNode> tiles = new ArrayDeque<>();

    /**
     * Mirrors {@link #tiles}'s membership exactly, kept in sync by every method that touches
     * {@code tiles} — exists purely so {@link #requireNotAlreadyPresent} is O(1), not O(length)
     * (code review finding, OPT-03): {@code ArrayDeque#contains} is a linear scan, so building a
     * long belt run tile-by-tile (each placement calling {@link #addHead}/{@link #addTail}) used to
     * cost O(1+2+...+N) = O(N²) total, purely for this membership check — the {@link #tick} loop
     * itself was never the problem (see that method's own javadoc on why it stays O(length)).
     */
    private final Set<TransportNode> membership = new HashSet<>();

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
    boolean isTail(TransportNode belt) {
        return tiles.getFirst() == belt;
    }

    /** Attach {@code node} as a new head (built directly past the current head). */
    void addHead(TransportNode node) {
        requireNotAlreadyPresent(node);
        tiles.addLast(node);
        membership.add(node);
        node.joinSegment(this);
    }

    /** Attach {@code node} as a new tail (built directly before the current tail). */
    void addTail(TransportNode node) {
        requireNotAlreadyPresent(node);
        tiles.addFirst(node);
        membership.add(node);
        node.joinSegment(this);
    }

    /**
     * Callers must leave whatever segment a tile is currently in before re-attaching it — see
     * {@code World#restoreBuilding}. A tile appearing twice in {@link #tiles} would silently
     * corrupt {@link #tick} and {@link #size}; fail loudly instead.
     */
    private void requireNotAlreadyPresent(TransportNode node) {
        if (membership.contains(node)) {
            throw new IllegalStateException("belt is already in this segment");
        }
    }

    /** Merge {@code other} (immediately past this segment's head, same direction) into this one. */
    void mergeHead(BeltSegment other) {
        for (TransportNode node : other.tiles) {
            tiles.addLast(node);
            membership.add(node);
            node.joinSegment(this);
        }
    }

    /**
     * Remove {@code node} from the segment (a building was demolished). Shrinks from an edge, or —
     * if the tile was in the middle — splits into two independent segments around the hole. Cargo
     * the removed tile was holding leaves with it, never duplicated onto a neighbor.
     *
     * <p>Public — unlike every other method here — because {@link TransportNode#leaveSegment()}
     * has no {@code default} implementation (it must check its own {@code segment} field for
     * {@code null} first, something only the implementing class itself can do) and so every
     * implementer, including a foreign one outside this package, calls this directly from its own
     * override. See {@link TransportNode#tickSegment} for how the OTHER package-private methods
     * here (kept that way) stay reachable without needing the same treatment.
     */
    public void remove(TransportNode node) {
        List<TransportNode> ordered = new ArrayList<>(tiles);
        int index = ordered.indexOf(node);
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
        node.joinSegment(null);
    }

    /**
     * Live one tick: the head tries to exit via {@code tryExit}; if it leaves, the next tile
     * toward the tail advances into its place, and so on down the chain. One head-to-tail pass
     * gives the "no more than one tile per tick" guarantee WITHIN this one segment.
     *
     * <p>A tile whose {@link TransportNode#arrivedThisTick} is set is skipped as a cargo SOURCE (it
     * can still be a valid destination — {@code next.held() == null} still checks its actual state):
     * it only just received that cargo via {@code accept}, earlier in the SAME world tick, from a
     * different segment or building entirely. Moving it again in this same pass is exactly the
     * cross-segment double-move P2-07 found; {@code TickScheduler} clears the mark once per frame,
     * before either traversal pass runs, so a tile is only ever blocked for the remainder of the
     * tick it arrived in — see P3-03, BUG_FIX_PROGRESS.md.
     */
    void tick(Predicate<ItemType> tryExit) {
        TransportNode next = null;
        var it = tiles.reversed().iterator();
        while (it.hasNext()) {
            TransportNode node = it.next();
            boolean eligibleSource = !node.arrivedThisTick();
            if (next == null) {
                ItemType head = node.held();
                if (eligibleSource && head != null && tryExit.test(head)) {
                    node.clearHeld();
                }
            } else if (eligibleSource && node.held() != null && next.held() == null) {
                next.setHeld(node.held());
                node.clearHeld();
            }
            next = node;
        }
    }
}
