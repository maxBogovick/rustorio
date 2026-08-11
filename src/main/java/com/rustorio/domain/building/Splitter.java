package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Accepts one item and alternates which of two directions it goes to — forward along {@link
 * #facing}, or one clockwise turn from it — by a strict round-robin, never by item identity (X-01,
 * DEV_TASKS.md). The OLD {@code Splitter} was really a filter with exactly one hardcoded rule
 * ({@code SortRule.ORE_FORWARD}, deleted along with the rest of that strategy); this is the
 * genre's actual "balancer" half of that split — {@link Filter} is the other, item-identity half.
 *
 * <p><b>Blocked-side fallback.</b> {@link #tick} always tries {@link #nextIsForward}'s side first;
 * only if THAT offer is refused does it try the other, open side THIS SAME tick — without flipping
 * {@link #nextIsForward}, so the assigned side gets first refusal again next time. A permanently
 * stuck side (a full chest, a dead-end belt, a press that will never want what's being sent) would
 * otherwise wedge the WHOLE building forever: {@link #nextIsForward} only ever flips on a success,
 * so once it lands on the stuck side it would keep retrying that exact side on every future tick
 * and never reach the other, perfectly healthy one again — starving a working output because its
 * unrelated sibling jammed. The earlier version refused this fallback outright to keep the split
 * exactly 50/50 under backpressure; that guarantee isn't worth trading total, permanent stalls of
 * both outputs for it — a temporarily uneven split while one side recovers is the far smaller cost.
 */
public final class Splitter implements Building, SettlesEachTick, InspectableBuilding {

    private final Direction facing;
    private @Nullable ItemType held;
    /** Which side gets the NEXT successfully delivered item — flips only on an actual successful delivery, never on a blocked attempt. */
    private boolean nextIsForward = true;
    /**
     * True for the rest of the CURRENT world tick if this splitter received its cargo via {@link
     * #accept} earlier in the same tick — the same mark {@link Belt#arrivedThisTick} carries, for the
     * same reason (N2, NEW_BUGS_PROGRESS.md). Without it, a chain of splitters all ticking in the
     * same pass relayed one item through every one of them within a single tick: the upstream one
     * ticks first, {@code accept} fills the downstream one, and the downstream one then ticks in that
     * very same frame. {@code TickScheduler} clears the mark once, before either pass runs.
     */
    private boolean arrivedThisTick;
    /** Which sprite {@link #appearance} draws — see {@link Furnace}'s own field javadoc for why this is injected rather than a hardcoded sprite constant. */
    private final BuildingPrototype prototype;

    /** Convenience for callers that only care about the vanilla prototype — see the 2-arg constructor for real injection (a modded splitter needs its own prototype here). */
    public Splitter(Direction facing) {
        this(facing, VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.SPLITTER)));
    }

    public Splitter(Direction facing, BuildingPrototype prototype) {
        this.facing = facing;
        this.prototype = prototype;
    }

    /** Convenience restore constructor for callers that only care about the vanilla prototype — see the 4-arg restore constructor for real injection. */
    Splitter(Direction facing, @Nullable ItemType held, boolean nextIsForward) {
        this(facing, held, nextIsForward,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.SPLITTER)));
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Splitter(Direction facing, @Nullable ItemType held, boolean nextIsForward, BuildingPrototype prototype) {
        this(facing, prototype);
        this.held = held;
        this.nextIsForward = nextIsForward;
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
        if (held == null || arrivedThisTick) {
            return; // arrived this same tick — the relay waits for the next one, exactly like a belt
        }
        Direction assigned = nextIsForward ? facing : facing.rotate();
        if (world.offerForward(x + assigned.dx(), y + assigned.dy(), held)) {
            held = null;
            nextIsForward = !nextIsForward;
            return;
        }
        // Assigned side refused — try the other, open side THIS tick instead of stalling both
        // outputs forever. nextIsForward stays put: the assigned side gets first refusal again
        // next time, so a healthy side isn't permanently starved by its stuck sibling, and a fully
        // healthy pair still alternates exactly as before (this branch never even runs for one).
        Direction fallback = nextIsForward ? facing.rotate() : facing;
        if (world.offerForward(x + fallback.dx(), y + fallback.dy(), held)) {
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
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(facing);
    }

    @Override
    public Optional<Direction> secondaryOutputDirection() {
        return Optional.of(facing.rotate());
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Splitter(facing.rotate(), held, nextIsForward, prototype));
    }

    @Override
    public SplitterState state() {
        return new SplitterState(facing, held, nextIsForward);
    }

    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        return List.of("Round-robin: alternates forward / secondary side");
    }
}
