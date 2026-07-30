package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.Optional;

/**
 * Decorator: doubles one specific building's throughput by wrapping it, rather than editing it —
 * distinct from a {@code Tech}, which speeds up every building of a kind at once. Modules stack:
 * a building in two layers is faster than in one (see {@link #tick}, wrapping wrapping wrapping).
 *
 * <p>{@code SpeedModule} doesn't know or care what's inside — furnace, press, anything — it just
 * calls {@code inner.tick} twice per real world tick. The wrapped building has no idea it's been
 * hurried along.
 *
 * <p><b>Owner decision (P2-03, BUG_FIX_PROGRESS.md):</b> option (A) — {@link Belt}s never get
 * wrapped in the first place; {@code UpgradeSpeedAction.apply} refuses them. Calling {@code
 * inner.tick} twice only does what it says for buildings whose {@code tick} is self-contained
 * (a furnace, a miner). A belt tile's {@code tick} only moves cargo when it happens to be its
 * segment's tail — doubling that call is a no-op on every other tile and doubles the throughput
 * of the WHOLE segment on the tail, neither of which is "this one tile got faster."
 */
public final class SpeedModule implements Building {

    private final Building inner;

    public SpeedModule(Building inner) {
        this.inner = inner;
    }

    /** What's wrapped — needed by {@link Building#unwrap} to find the real building underneath. */
    Building inner() {
        return inner;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        inner.tick(world, x, y);
        inner.tick(world, x, y);
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        return inner.accept(world, item);
    }

    @Override
    public Appearance appearance() {
        return inner.appearance();
    }

    @Override
    public Optional<ItemType> heldItem() {
        return inner.heldItem();
    }

    @Override
    public int speedLevel() {
        return 1 + inner.speedLevel();
    }

    @Override
    public BuildingType type() {
        return inner.type();
    }

    @Override
    public BuildingMemento memento() {
        return inner.memento();
    }

    @Override
    public boolean prefersDescendingTick() {
        // Mandatory delegation: prefersDescendingTick has a default, so the compiler won't force
        // this override — skip it and an upgraded leftward/upward belt would silently get `true`
        // from the wrapper instead of the real belt's answer, teleporting cargo across the chain.
        return inner.prefersDescendingTick();
    }

    @Override
    public Optional<Direction> outputDirection() {
        // Same forgetfulness risk as prefersDescendingTick above: outputDirection also defaults to
        // empty, and without this line an upgraded belt/furnace/tunnel would silently lose its
        // direction arrow on screen — the building would work, but render as if it had no facing.
        return inner.outputDirection();
    }

    @Override
    public Optional<Direction> secondaryOutputDirection() {
        return inner.secondaryOutputDirection();
    }

    /**
     * Same forgetfulness risk as {@link #outputDirection} above (X-03, DEV_TASKS.md): both
     * default to {@code 1}, so skipping this delegation would silently shrink an upgraded
     * multi-cell building's footprint back to a single cell the instant {@code UpgradeSpeedAction}
     * wraps it — {@code World} would then free/reserve the wrong set of cells on its next
     * demolition or restore.
     */
    @Override
    public int footprintWidth() {
        return inner.footprintWidth();
    }

    @Override
    public int footprintHeight() {
        return inner.footprintHeight();
    }

    /**
     * Same forgetfulness risk as {@link #outputDirection} above: without this override, {@code
     * rotatedClockwise} would default to empty and an upgraded building could never be rotated in
     * place again. Rotating the INNER building and re-wrapping it (rather than rotating this
     * wrapper somehow) preserves however many {@code SpeedModule} layers deep this call started —
     * a doubly-upgraded building rotates through both layers via the recursive call and comes back
     * wrapped twice, same as it went in.
     */
    @Override
    public Optional<Building> rotatedClockwise() {
        return inner.rotatedClockwise().map(SpeedModule::new);
    }
}
