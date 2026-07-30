package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Accepts one item and alternates which of two directions it goes to — forward along {@link
 * #facing}, or one clockwise turn from it — by a strict round-robin, never by item identity (X-01,
 * DEV_TASKS.md). The OLD {@code Splitter} was really a filter with exactly one hardcoded rule
 * ({@code SortRule.ORE_FORWARD}, deleted along with the rest of that strategy); this is the
 * genre's actual "balancer" half of that split — {@link Filter} is the other, item-identity half.
 *
 * <p><b>Owner decision:</b> if the side {@link #nextIsForward} currently points at is blocked,
 * this building WAITS rather than opportunistically routing the held item to the other, open side.
 * The alternative — falling back to whichever side happens to be free — would let one side's own
 * backpressure silently skew the split away from 50/50, which defeats the entire point of a
 * balancer: a round-robin's honesty comes from committing to the assigned side even when it costs
 * a stall, the same "hold until delivered, don't improvise" discipline every other producer here
 * already follows.
 */
public final class Splitter implements Building, SettlesEachTick {

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

    public Splitter(Direction facing) {
        this.facing = facing;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Splitter(Direction facing, @Nullable ItemType held, boolean nextIsForward) {
        this(facing);
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
        Direction direction = nextIsForward ? facing : facing.rotate();
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
            nextIsForward = !nextIsForward;
        }
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(VanillaSprites.SPLITTER);
    }

    @Override
    public BuildingType type() {
        return BuildingType.SPLITTER;
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
        return Optional.of(new Splitter(facing.rotate(), held, nextIsForward));
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.SplitterState(facing, held, nextIsForward);
    }
}
