package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
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
    /** Which sprite {@link #appearance} draws — see {@link Furnace}'s own field javadoc for why this is injected rather than a hardcoded sprite constant. */
    private final BuildingPrototype prototype;

    /** Convenience for callers that only care about the vanilla item set and prototype — see the 4-arg constructor for real injection. */
    public Filter(Direction facing, ItemType filterItem) {
        this(facing, filterItem, VanillaItems.frozen());
    }

    /** Convenience for callers that only care about the vanilla prototype — see the 4-arg constructor for real injection (a modded filter needs its own prototype here). */
    public Filter(Direction facing, ItemType filterItem, Registry<ItemType> items) {
        this(facing, filterItem, items, VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.FILTER)));
    }

    public Filter(Direction facing, ItemType filterItem, Registry<ItemType> items, BuildingPrototype prototype) {
        this.facing = facing;
        this.filterItem = filterItem;
        this.items = items;
        this.prototype = prototype;
    }

    /** Convenience restore constructor for callers that only care about the vanilla prototype — see the 5-arg restore constructor for real injection. */
    Filter(Direction facing, ItemType filterItem, @Nullable ItemType held, Registry<ItemType> items) {
        this(facing, filterItem, held, items,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.FILTER)));
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Filter(Direction facing, ItemType filterItem, @Nullable ItemType held, Registry<ItemType> items,
            BuildingPrototype prototype) {
        this(facing, filterItem, items, prototype);
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
        return Optional.of(new Filter(facing.rotate(), filterItem, held, items, prototype));
    }

    @Override
    public FilterState state() {
        return new FilterState(facing, held, filterItem);
    }
}
