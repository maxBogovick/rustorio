package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Holds one item and, once per tick, nudges it toward its neighbor in {@link #direction}.
 *
 * <p>A straight run of same-direction belts is owned by a shared {@link BeltSegment}, which
 * decides in what order cargo may advance and runs the whole run's tick as one pass instead of N
 * independent ones. Each tile still holds its own cargo ({@link #held}) — the segment only
 * reorders it between tiles.
 */
public final class Belt implements Building, TransportNode, SettlesEachTick {

    private final Direction direction;
    private @Nullable ItemType held;
    /**
     * Set right after construction, by {@code World.attachToSegment} calling {@link
     * #joinSegment} — never left unset while this tile is placed in the world; only null in the
     * brief window before that call, and again after {@link #leaveSegment} on demolition. {@code
     * NullAway.Init} below trusts that placement protocol instead of forcing every read site
     * (segment().isTail(), segment().tick(), …) to null-check a field that's a genuine bug to see
     * null while ticking.
     */
    private @Nullable BeltSegment segment;
    /**
     * True for the rest of the CURRENT world tick if this tile received cargo via {@link #accept}
     * earlier in the same tick (a cross-segment or cross-building delivery — never set by the
     * intra-segment cascade in {@link BeltSegment#tick}, which moves cargo by direct field access,
     * not through {@code accept}). {@link BeltSegment#tick} treats a marked tile as ineligible to
     * move further this frame; {@code TickScheduler} clears every belt's mark once, before either
     * traversal pass runs. Without this, a descending-phase segment (say, DOWN) handing cargo to
     * an ascending-phase segment (say, LEFT) let the receiving segment — ticking later in the very
     * same frame — move that same item a second tile. See P2-07/P3-03, BUG_FIX_PROGRESS.md.
     */
    private boolean arrivedThisTick;

    public Belt(Direction direction) {
        this.direction = direction;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Belt(Direction direction, @Nullable ItemType held) {
        this.direction = direction;
        this.held = held;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    /**
     * A fresh {@link Belt} facing the rotated direction, carrying the same cargo — segment
     * membership is NOT copied over: {@code com.rustorio.domain.action.RotateAction} reaches this
     * tile through {@code World.removeBuilding}/{@code restoreBuilding}, the same detach/reattach
     * path a demolish or a {@link SpeedModule} upgrade already uses, which is what lets this tile
     * leave its old segment (splitting it if this was a middle tile — see {@code
     * BeltSegment#remove}) and join whatever matches its new direction.
     */
    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Belt(direction.rotate(), held));
    }

    /**
     * Join (or leave, on removal, with {@code null}) a segment — called only by {@code World}.
     * Public because {@link TransportNode} requires it (interface methods can't be narrower); treat
     * it as package-private in spirit — general code has no business calling this directly.
     */
    @Override
    public void joinSegment(@Nullable BeltSegment segment) {
        this.segment = segment;
    }

    /** The segment this tile currently belongs to — never null while the tile is placed (see field javadoc). */
    @Override
    public BeltSegment segment() {
        return Objects.requireNonNull(segment);
    }

    /**
     * Leave this tile's segment — package-private, reached from {@code World} only through {@link
     * BuildingFactory#detachTransportNode} (P3-02, BUG_FIX_PROGRESS.md), when the belt is
     * demolished or about to be re-attached idempotently.
     */
    @Override
    public void leaveSegment() {
        if (segment != null) {
            segment.remove(this);
        }
    }

    /** Public only because {@link TransportNode} requires it — see that interface's javadoc; callers stay {@link BeltSegment}. */
    @Override
    public @Nullable ItemType held() {
        return held;
    }

    @Override
    public void setHeld(ItemType item) {
        held = item;
    }

    @Override
    public void clearHeld() {
        held = null;
    }

    /** Whether {@link BeltSegment#tick} must refuse to move this tile further THIS frame. */
    @Override
    public boolean arrivedThisTick() {
        return arrivedThisTick;
    }

    /**
     * Called once per world tick, before any building ticks — see {@code TickScheduler}. Public
     * only because {@link SettlesEachTick} requires it (interface methods can't be narrower); no
     * caller besides {@code TickScheduler} should call this directly.
     */
    @Override
    public void clearArrivalMark() {
        arrivedThisTick = false;
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (held != null) {
            return false;
        }
        held = item;
        arrivedThisTick = true;
        return true;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        BeltSegment mySegment = segment();
        if (!mySegment.isTail(this)) {
            return;
        }
        int exitX = x + direction.dx() * mySegment.size();
        int exitY = y + direction.dy() * mySegment.size();
        mySegment.tick(item -> world.offerForward(exitX, exitY, item));
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return held == null ? Appearance.of(VanillaSprites.BELT_EMPTY) : Appearance.of(VanillaSprites.BELT_FULL);
    }

    @Override
    public BuildingType type() {
        return BuildingType.BELT;
    }

    /**
     * A rightward/downward belt fits the world's default traversal (high coordinates first); a
     * leftward/upward one needs the reverse, or it would push a neighbor that hasn't ticked yet
     * this frame and cargo would skip the whole chain in one tick instead of one tile.
     */
    @Override
    public boolean prefersDescendingTick() {
        return direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.BeltState(direction, held);
    }
}
