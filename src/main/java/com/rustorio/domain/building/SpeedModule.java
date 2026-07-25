package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.World;
import java.util.Optional;

/**
 * Decorator: doubles one specific building's throughput by wrapping it, rather than editing it —
 * distinct from a {@code Tech}, which speeds up every building of a kind at once. Modules stack:
 * a building in two layers is faster than in one (see {@link #tick}, wrapping wrapping wrapping).
 *
 * <p>{@code SpeedModule} doesn't know or care what's inside — furnace, press, anything — it just
 * calls {@code inner.tick} twice per real world tick. The wrapped building has no idea it's been
 * hurried along.
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
    public void tick(World world, int x, int y) {
        inner.tick(world, x, y);
        inner.tick(world, x, y);
    }

    @Override
    public boolean accept(World world, Item item) {
        return inner.accept(world, item);
    }

    @Override
    public Appearance appearance() {
        return inner.appearance();
    }

    @Override
    public Optional<Item> heldItem() {
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
}
