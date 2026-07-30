package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.Tech;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One half of a tunnel pair that carries an item under an obstacle, bypassing the usual
 * "offer to the neighbor ahead" hand-off. The {@link Kind#IN} half takes from an ordinary
 * neighbor behind it and, every tick, searches ahead along its {@link Direction} for the nearest
 * same-orientation {@link Kind#OUT} within {@link #MAX_RANGE} tiles, handing the item to it
 * directly. The {@code OUT} half then pushes forward like a plain {@link Belt}.
 */
public final class UndergroundBelt implements Building {

    public enum Kind {
        IN, OUT
    }

    private static final int MAX_RANGE = 4;

    private final Kind kind;
    private final Direction direction;
    private @Nullable ItemType held;
    /**
     * True for the rest of the CURRENT world tick if this {@code IN} tile received cargo via
     * {@link #accept} earlier in the same tick — the tunnel analogue of {@link Belt#arrivedThisTick}
     * (found in a post-Phase-4 review, not an original P-numbered task). {@link #direction} alone
     * decides which pass an entrance ticks in (P2-06), so a feeder belt of the OTHER pass can
     * deliver into this entrance in an earlier phase of the same frame; without this mark, {@link
     * #tickIn} would relay that cargo to the exit immediately, skipping the one-tick settle every
     * other cross-boundary hand-off already gets. {@code TickScheduler} clears the mark once, before
     * either pass runs, same as it does for every {@link Belt}.
     */
    private boolean arrivedThisTick;

    public UndergroundBelt(Kind kind, Direction direction) {
        this.kind = kind;
        this.direction = direction;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    UndergroundBelt(Kind kind, Direction direction, @Nullable ItemType held) {
        this(kind, direction);
        this.held = held;
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (kind != Kind.IN || held != null) {
            return false;
        }
        held = item;
        arrivedThisTick = true;
        return true;
    }

    /** Whether {@link #tickIn} must refuse to relay this tile's cargo further THIS frame. */
    boolean arrivedThisTick() {
        return arrivedThisTick;
    }

    /** Called once per world tick, before any building ticks — see {@code TickScheduler}. */
    void clearArrivalMark() {
        arrivedThisTick = false;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held == null) {
            return;
        }
        if (kind == Kind.IN) {
            tickIn(world, x, y);
        } else {
            tickOut(world, x, y);
        }
    }

    private void tickIn(TickContext world, int x, int y) {
        if (arrivedThisTick) {
            return; // received this same tick from a cross-phase feeder — relay waits for the next one
        }
        findPartner(world, x, y).ifPresent(partner -> {
            if (partner.held == null) {
                partner.held = held;
                held = null;
            }
        });
    }

    /**
     * Find this tunnel entrance's exit ahead along its direction — the same search {@link
     * #tickIn} performs, but without moving any cargo. Exposed separately so the "orphaned
     * entrance" overlay can highlight an entrance with no reachable partner without pretending to
     * deliver anything.
     */
    public Optional<UndergroundBelt> findPartner(TickContext world, int x, int y) {
        if (kind != Kind.IN) {
            return Optional.empty();
        }
        int dx = direction.dx();
        int dy = direction.dy();
        for (int step = 1; step <= effectiveRange(world); step++) {
            Optional<Building> candidate = world.peek(x + dx * step, y + dy * step);
            if (candidate.isPresent()
                    && Building.unwrap(candidate.get()) instanceof UndergroundBelt other
                    && other.kind == Kind.OUT
                    && other.direction == direction) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    private static int effectiveRange(TickContext world) {
        return world.research().isUnlocked(Tech.LONG_TUNNEL) ? MAX_RANGE * 2 : MAX_RANGE;
    }

    private void tickOut(TickContext world, int x, int y) {
        ItemType cargo = held;
        if (cargo == null) {
            return;
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), cargo)) {
            held = null;
        }
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(kind == Kind.IN ? VanillaSprites.UNDERGROUND_IN : VanillaSprites.UNDERGROUND_OUT);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new UndergroundBelt(kind, direction.rotate(), held));
    }

    @Override
    public BuildingType type() {
        return kind == Kind.IN ? BuildingType.UNDERGROUND_IN : BuildingType.UNDERGROUND_OUT;
    }

    /**
     * Direction alone decides the pass, exactly like {@link Belt} — {@code kind} must NOT factor
     * in. It used to (see P2-06, BUG_FIX_PROGRESS.md): {@code IN} always preferred the descending
     * pass regardless of direction, which for {@code LEFT} put the entrance in the descending pass
     * and the exit in the ascending pass of the SAME frame — cargo crossed the whole tunnel in one
     * tick instead of travelling like a belt. Tying both halves to direction alone keeps them in
     * the same pass, so {@code IN} still hands off to {@code OUT} one tick early — {@code OUT} is
     * "ahead" along {@code direction}, so it's visited first in whichever pass that direction
     * prefers, and finds nothing to move until {@code IN} sets {@link #held} the tick before.
     */
    @Override
    public boolean prefersDescendingTick() {
        return direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.UndergroundBeltState(kind, direction, held);
    }
}
