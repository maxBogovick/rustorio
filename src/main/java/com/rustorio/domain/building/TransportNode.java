package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import org.jspecify.annotations.Nullable;

/**
 * One tile of a {@link BeltSegment}'s straight run — the low-level surface {@link
 * BeltSegment#tick} cascades cargo across several tiles in one pass, deliberately bypassing {@link
 * Building#accept}/{@link Building#heldItem()} (an intra-segment move must NOT count as "arrived
 * this tick", or the cross-segment double-move guard breaks — see {@link Belt#arrivedThisTick}).
 * Ordinary code still reads cargo through {@link Building#heldItem()} same as always — this
 * interface exists only so {@link BeltSegment} can hold and cascade across tiles of a type other
 * than {@link Belt} itself; {@link UndergroundBelt} does NOT implement it — it never joins a
 * segment, each entrance finds its own exit by direct search instead (see {@link
 * UndergroundBelt#findPartner}).
 *
 * <p>Genuinely a moddable capability — a class living outside this package can implement
 * it, exactly like {@link Building} itself (see the phase's own acceptance capstone). That needed
 * {@link BeltSegment} and {@link SettlesEachTick} to become {@code public} too: this interface's
 * own methods reference {@link BeltSegment} in their signatures, and {@code TickScheduler} clears
 * arrival marks by {@code instanceof SettlesEachTick} — a foreign implementer can't satisfy either
 * without being able to name both types. Was originally public only incidentally (for {@code
 * com.rustorio.domain.world.World}'s own belt-neighbor lookups, before mods implementing it was
 * even possible) — that history is why the cargo mutator methods below still read "public only
 * because the interface requires it," not "meant for general callers."
 */
public interface TransportNode {

    Direction direction();

    /**
     * The cargo held right now — {@code null} if empty. Package-private-in-spirit despite being a
     * public interface method (Java has no way to keep it narrower once it's declared here): only
     * {@link BeltSegment} should call this — everything else must go through {@link
     * Building#heldItem()} instead.
     */
    @Nullable ItemType held();

    /** Set cargo directly, bypassing {@link Building#accept}'s capacity check and arrival mark — {@link BeltSegment} only. */
    void setHeld(ItemType item);

    /** Clear cargo directly — {@link BeltSegment} only. */
    void clearHeld();

    /** Whether {@link BeltSegment#tick} must refuse to move this tile further THIS frame. */
    boolean arrivedThisTick();

    /** Join (or leave, with {@code null}) a segment — called only by {@link BeltSegment} itself, from the placement/removal path. */
    void joinSegment(@Nullable BeltSegment segment);

    /** Leave this tile's current segment, if any. */
    void leaveSegment();

    /** The segment this tile currently belongs to — never null while the tile is placed. */
    BeltSegment segment();

    /**
     * Join this tile to whichever segment(s) its same-direction neighbors belong to — package-
     * private, reached from {@code World} only through {@code BuildingFactory}'s narrow door (P3-02,
     * BUG_FIX_PROGRESS.md): {@code World} finds the neighbor behind/ahead (it owns the cell map),
     * hands them to that door, and everything past it — {@link BeltSegment} manipulation — stays
     * inside this package. A {@code default} method, not one every implementer repeats: expressed
     * entirely in terms of this interface's own {@link #direction()}/{@link #segment()}, so any
     * {@link TransportNode} gets correct merge/split behavior for free, without reimplementing it.
     *
     * <p>Call order doesn't matter (in particular for save-game loading, which restores tiles in
     * arbitrary map-key order): whichever tile appears second is the one whose call discovers the
     * already-standing neighbor and merges segments — the result is the same regardless of which
     * of the two tiles was restored first.
     */
    default void attachToNeighbors(@Nullable TransportNode behind, @Nullable TransportNode ahead) {
        if (behind != null) {
            behind.segment().addHead(this);
            if (ahead != null && ahead.segment() != behind.segment()) {
                behind.segment().mergeHead(ahead.segment()); // new tile landed BETWEEN two segments
            }
        } else if (ahead != null) {
            ahead.segment().addTail(this);
        } else {
            new BeltSegment(direction()).addHead(this); // no transport neighbors — a fresh one-tile segment
        }
    }

    /**
     * Drive this tile's {@link Building#tick}: only the segment's TAIL does anything (every other
     * tile's own call is a no-op), because the whole run advances together in one {@link
     * BeltSegment#tick} pass rather than each tile pushing independently — see {@link BeltSegment}'s
     * own javadoc for why. A {@code default} method, same reasoning as {@link #attachToNeighbors}:
     * expressed entirely in terms of this interface's own {@link #direction()}/{@link #segment()},
     * so {@link BeltSegment}'s own package-private {@code isTail}/{@code size}/{@code tick} methods
     * never need to be public themselves — a {@link TransportNode} implementer living OUTSIDE this
     * package (a mod) calls this from its own {@code Building.tick()} override instead of reaching
     * into those internals directly, which it structurally can't do from another package. {@link
     * Belt#tick} itself calls this too, rather than duplicating the logic.
     */
    default void tickSegment(TickContext world, int x, int y) {
        BeltSegment mySegment = segment();
        if (!mySegment.isTail(this)) {
            return;
        }
        int exitX = x + direction().dx() * mySegment.size();
        int exitY = y + direction().dy() * mySegment.size();
        mySegment.tick(item -> world.offerForward(exitX, exitY, item));
    }
}
