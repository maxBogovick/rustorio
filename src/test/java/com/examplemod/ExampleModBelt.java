package com.examplemod;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BeltSegment;
import com.rustorio.domain.building.BeltState;
import com.rustorio.domain.building.SettlesEachTick;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.TransportNode;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A transport node written from scratch, living OUTSIDE {@code com.rustorio.domain.building} —
 * a stand-in for a mod's own belt-like building. Deliberately unremarkable behavior (moves one
 * item toward {@link #direction} per tick, same as the vanilla {@code Belt}): the point of this
 * class isn't to do something exotic, it's to prove a genuinely foreign class can implement
 * {@link TransportNode} and fuse into the SAME {@link BeltSegment} cascade a vanilla belt joins —
 * the phase's own acceptance criterion.
 *
 * <p>{@link #state()} returns a real {@link BeltState} — this node's own fields are exactly that
 * shape (direction + held cargo), so reusing the vanilla record (now {@code public}, exactly so a
 * mod CAN reuse it) needs no new state type of its own. Genuinely persistable, not a stand-in
 * limitation like {@code ExampleModBuilding}'s.
 */
final class ExampleModBelt implements Building, TransportNode, SettlesEachTick {

    private final ContentId prototypeId;
    private final Direction direction;
    private @Nullable ItemType held;
    private @Nullable BeltSegment segment;
    private boolean arrivedThisTick;

    ExampleModBelt(ContentId prototypeId, Direction direction) {
        this.prototypeId = prototypeId;
        this.direction = direction;
    }

    /** Restore constructor — mirrors the vanilla {@code Belt}'s own, just reachable from outside {@code com.rustorio.domain.building} since this class lives elsewhere. */
    ExampleModBelt(ContentId prototypeId, Direction direction, @Nullable ItemType held) {
        this.prototypeId = prototypeId;
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

    @Override
    public void joinSegment(@Nullable BeltSegment segment) {
        this.segment = segment;
    }

    @Override
    public BeltSegment segment() {
        return Objects.requireNonNull(segment);
    }

    @Override
    public void leaveSegment() {
        if (segment != null) {
            segment.remove(this);
        }
    }

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

    @Override
    public boolean arrivedThisTick() {
        return arrivedThisTick;
    }

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
        // BeltSegment's own isTail/size/tick methods are package-private on purpose (see its class
        // javadoc) — this default method is exactly the door TransportNode leaves open for a
        // foreign implementer to drive the segment cascade without needing them.
        tickSegment(world, x, y);
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        // Borrows the vanilla belt sprites — a real mod would register its own via a texture
        // index (Phase 3); this test isn't about assets.
        return held == null ? Appearance.of(VanillaSprites.BELT_EMPTY) : Appearance.of(VanillaSprites.BELT_FULL);
    }

    @Override
    public BuildingType type() {
        // Borrows a vanilla kind — a real mod would register its own via BuildingPrototype once
        // BuildingFactory.create/restore's own dispatch opens up (E5-07 already lets it — see the
        // steel press half of this same acceptance test), not this class's job.
        return BuildingType.BELT;
    }

    /**
     * Overridden for the same reason {@code Furnace} overrides it: {@link #type()} answers {@code
     * BELT} (a borrowed vanilla kind), which is not this node's real governing prototype — the
     * {@link Building} interface's own default derives a prototype id FROM {@code type()}, which
     * would wrongly resolve to the vanilla belt prototype and lose this node's modded identity
     * across a save/load round trip.
     */
    @Override
    public ContentId prototypeId() {
        return prototypeId;
    }

    @Override
    public boolean prefersDescendingTick() {
        return direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    @Override
    public BeltState state() {
        return new BeltState(direction, held);
    }
}
