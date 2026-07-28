package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
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
public final class Filter implements Building {

    private final Direction facing;
    private Item filterItem;
    private @Nullable Item held;
    /** Same one-tick settle every other relay carries — see {@link Splitter#arrivedThisTick} (N2, NEW_BUGS_PROGRESS.md). */
    private boolean arrivedThisTick;

    public Filter(Direction facing, Item filterItem) {
        this.facing = facing;
        this.filterItem = filterItem;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Filter(Direction facing, Item filterItem, @Nullable Item held) {
        this(facing, filterItem);
        this.held = held;
    }

    /** Advance to the next {@link Item} this filter passes forward, wrapping around — the player's remedy for "picked the wrong one," same shape as {@code Furnace#cycleRecipe}. */
    public Item cycleFilterItem() {
        Item[] items = Item.values();
        filterItem = items[(filterItem.ordinal() + 1) % items.length];
        return filterItem;
    }

    /** Which item currently passes forward — everything else goes to the rotated side. For the inspection panel. */
    public Item filterItem() {
        return filterItem;
    }

    @Override
    public boolean accept(TickContext world, Item item) {
        if (held != null) {
            return false;
        }
        held = item;
        arrivedThisTick = true;
        return true;
    }

    /** Called once per world tick, before any building ticks — see {@code TickScheduler}. */
    void clearArrivalMark() {
        arrivedThisTick = false;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held == null || arrivedThisTick) {
            return; // arrived this same tick — the relay waits for the next one, exactly like a belt
        }
        Direction direction = held == filterItem ? facing : facing.rotate();
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    @Override
    public Optional<Item> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.FILTER);
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
        return Optional.of(new Filter(facing.rotate(), filterItem, held));
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.FilterState(facing, held, filterItem);
    }
}
