package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Takes one item from whatever hands it one and pushes it straight ahead — a single cell
 * restoring direct transfer between two adjacent machines (X-01, DEV_TASKS.md), the thing D-01
 * removed when delivery stopped broadcasting to any accepting neighbor and started addressing
 * only the single cell ahead of a building's own facing. Mechanically identical to one {@link
 * Belt} tile holding one item — its own {@link BuildingType}/cost/sprite is what makes it a
 * distinct, PRICED decision rather than "just build a belt," matching the design audit's framing
 * (§2.1): direct insertion between neighbors is legitimate, but costs a cell and a build, not free.
 *
 * <p><b>Scope note (owner decision).</b> A real inserter in the genre this borrows from actively
 * reaches into the cell behind it regardless of what THAT building's own facing is (it can drain a
 * chest sitting sideways, say, that never offers anything forward on its own). This one still only
 * RECEIVES what's pushed into it via {@link #accept} — the same discipline every other single-slot
 * building here already follows. Reaching into an arbitrary neighbor's held item regardless of ITS
 * facing would need a new mutating capability on {@link Building} itself — nothing currently
 * exposes one ({@link Building#heldItem()} is deliberately read-only, Effective Java Item 55) —
 * which is a bigger change than this task's own affected-files list covers. Left as a documented
 * gap, not a silent one, for a follow-up if that stronger form turns out to actually be needed.
 */
public final class Inserter implements Building, SettlesEachTick {

    private final Direction direction;
    private @Nullable ItemType held;
    /** Same one-tick settle every other relay carries — see {@link Splitter#arrivedThisTick} (N2, NEW_BUGS_PROGRESS.md). */
    private boolean arrivedThisTick;
    /** Which sprite {@link #appearance} draws — see {@link Furnace}'s own field javadoc for why this is injected rather than a hardcoded sprite constant. */
    private final BuildingPrototype prototype;

    /** Convenience for callers that only care about the vanilla prototype — see the 2-arg constructor for real injection (a modded inserter needs its own prototype here). */
    public Inserter(Direction direction) {
        this(direction, VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.INSERTER)));
    }

    public Inserter(Direction direction, BuildingPrototype prototype) {
        this.direction = direction;
        this.prototype = prototype;
    }

    /** Convenience restore constructor for callers that only care about the vanilla prototype — see the 3-arg restore constructor for real injection. */
    Inserter(Direction direction, @Nullable ItemType held) {
        this(direction, held, VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.INSERTER)));
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Inserter(Direction direction, @Nullable ItemType held, BuildingPrototype prototype) {
        this(direction, prototype);
        this.held = held;
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
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture());
    }

    @Override
    public BuildingType type() {
        return BuildingType.INSERTER;
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Inserter(direction.rotate(), held, prototype));
    }

    @Override
    public InserterState state() {
        return new InserterState(direction, held);
    }
}
