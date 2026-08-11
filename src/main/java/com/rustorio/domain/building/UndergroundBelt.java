package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaTechEffects;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One half of a tunnel pair that carries an item under an obstacle, bypassing the usual
 * "offer to the neighbor ahead" hand-off. The {@link Kind#IN} half takes from an ordinary
 * neighbor behind it and, every tick, searches ahead along its {@link Direction} for the nearest
 * same-orientation {@link Kind#OUT} within {@link #MAX_RANGE} tiles, handing the item to it
 * directly. The {@code OUT} half then pushes forward like a plain {@link Belt}.
 */
public final class UndergroundBelt implements Building, SettlesEachTick, InspectableBuilding {

    public enum Kind {
        IN, OUT
    }

    private static final int MAX_RANGE = 4;

    private final Kind kind;

    private final Direction direction;
    private @Nullable ItemType held;
    /** Which sprite {@link #appearance} draws — see {@link Furnace}'s own field javadoc for why this is injected rather than a hardcoded sprite constant. */
    private final BuildingPrototype prototype;
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

    /** Convenience for callers that only care about the vanilla prototype matching {@code kind} — see the 3-arg constructor for real injection (a modded tunnel needs its own prototype here). */
    public UndergroundBelt(Kind kind, Direction direction) {
        this(kind, direction, vanillaPrototype(kind));
    }

    public UndergroundBelt(Kind kind, Direction direction, BuildingPrototype prototype) {
        this.kind = kind;
        this.direction = direction;
        this.prototype = prototype;
    }

    /** Convenience restore constructor for callers that only care about the vanilla prototype matching {@code kind} — see the 4-arg restore constructor for real injection. */
    UndergroundBelt(Kind kind, Direction direction, @Nullable ItemType held) {
        this(kind, direction, held, vanillaPrototype(kind));
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    UndergroundBelt(Kind kind, Direction direction, @Nullable ItemType held, BuildingPrototype prototype) {
        this(kind, direction, prototype);
        this.held = held;
    }

    /** {@link Kind#IN}/{@link Kind#OUT} back two separate {@link BuildingType}s (unlike every other archetype except {@link Furnace}'s three) — the vanilla default a convenience constructor falls back to must match whichever one {@code kind} names. */
    private static BuildingPrototype vanillaPrototype(Kind kind) {
        BuildingType type = kind == Kind.IN ? BuildingType.UNDERGROUND_IN : BuildingType.UNDERGROUND_OUT;
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type));
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

    /**
     * Called once per world tick, before any building ticks — see {@code TickScheduler}. Public
     * only because {@link SettlesEachTick} requires it; no caller besides {@code TickScheduler}
     * should call this directly.
     */
    @Override
    public void clearArrivalMark() {
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
        int range = effectiveRange(world);
        for (int step = 1; step <= range; step++) {
            Optional<Building> candidate = world.peek(x + dx * step, y + dy * step);
            if (candidate.isPresent()
                    && candidate.get() instanceof UndergroundBelt other
                    && other.kind == Kind.OUT
                    && other.direction == direction) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    private static int effectiveRange(TickContext world) {
        return world.research().hasEffect(VanillaTechEffects.LONG_TUNNEL) ? MAX_RANGE * 2 : MAX_RANGE;
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

    /** Always {@code WORKING}: this archetype has no notion of being stuck — see {@link Building#status()}. */
    @Override
    public BuildingStatus status() {
        return BuildingStatus.WORKING;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture());
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new UndergroundBelt(kind, direction.rotate(), held, prototype));
    }

    /**
     * Whether this is the entrance of a tunnel pair rather than its exit — the question callers
     * actually have, answered by the building instead of by comparing its {@code type()} against a
     * vanilla constant. That comparison is what stopped a modded tunnel from ever being recognised
     * as an entrance: a mod's prototype borrows {@code UNDERGROUND_IN} only if it happens to, and
     * its identity is its own id.
     */
    public boolean isEntrance() {
        return kind == Kind.IN;
    }


    @Override
    public ContentId prototypeId() {
        return prototype.id();
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
    public UndergroundBeltState state() {
        return new UndergroundBeltState(kind, direction, held);
    }

    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        if (!isEntrance()) {
            return List.of("(exit — pairing shown at its entrance)");
        }
        boolean paired = findPartner(world, x, y).isPresent();
        return List.of("Paired: " + (paired ? "yes" : "NO — out of range or no matching exit"));
    }
}
