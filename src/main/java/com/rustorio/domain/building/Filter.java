package com.rustorio.domain.building;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.VanillaItems;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Accepts any item and routes it to one of two directions by IDENTITY — forward along {@link
 * #facing} if it equals {@link #filterItem}, to the rotated side otherwise. The item-identity
 * half of the old combined {@code Splitter} (X-01, DEV_TASKS.md); {@link Splitter} itself is now
 * the OTHER half — a real round-robin balancer that doesn't look at item identity at all.
 *
 * <p>{@link #filterItem} is plain DATA the player picks ({@link #cycleFilterItem}), not a {@code
 * SortRule} strategy instance — the card's own explicit correction: before this, there was exactly
 * one hardcoded rule ({@code SortRule.ORE_FORWARD}, now deleted) and no way for a player to choose
 * a different one at all.
 */
public final class Filter implements Building, SettlesEachTick {

    private final Direction facing;
    private ItemType filterItem;
    private @Nullable ItemType held;
    /** Same one-tick settle every other relay carries — see {@link Splitter#arrivedThisTick} (N2, NEW_BUGS_PROGRESS.md). */
    private boolean arrivedThisTick;
    /** What {@link #cycleFilterItem} cycles through — the registry this filter was actually built with, not always the vanilla one (code review finding S2). */
    private final Registry<ItemType> items;

    /** Convenience for callers that only care about the vanilla item set — see the 3-arg constructor for real injection. */
    public Filter(Direction facing, ItemType filterItem) {
        this(facing, filterItem, VanillaItems.frozen());
    }

    public Filter(Direction facing, ItemType filterItem, Registry<ItemType> items) {
        this.facing = facing;
        this.filterItem = filterItem;
        this.items = items;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Filter(Direction facing, ItemType filterItem, @Nullable ItemType held, Registry<ItemType> items) {
        this(facing, filterItem, items);
        this.held = held;
    }

    /** Advance to the next {@link ItemType} this filter passes forward, wrapping around — the player's remedy for "picked the wrong one," same shape as {@code Furnace#cycleRecipe}. */
    public ItemType cycleFilterItem() {
        int next = (items.rawId(filterItem.id()) + 1) % items.size();
        filterItem = items.get(next);
        return filterItem;
    }

    /** Which item currently passes forward — everything else goes to the rotated side. For the inspection panel. */
    public ItemType filterItem() {
        return filterItem;
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
        Direction direction = held.equals(filterItem) ? facing : facing.rotate();
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
        return Appearance.of(VanillaSprites.FILTER);
    }

    @Override
    public BuildingType type() {
        return BuildingType.FILTER;
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
        return Optional.of(new Filter(facing.rotate(), filterItem, held, items));
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.FilterState(facing, held, filterItem);
    }
}
