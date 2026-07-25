package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.SortRule;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Accepts one item and routes it to one of two directions — forward along {@link #facing}, or
 * one clockwise turn from it — decided entirely by the injected {@link SortRule} (Strategy
 * pattern): this class only knows "ask the rule, then push that way," never "ore goes forward."
 *
 * <p><b>Known compromise:</b> {@link #rule} does not survive save/load. {@link #memento()} has
 * nowhere to put it — {@link BuildingMemento.SplitterState} only carries {@link #facing} and
 * {@link #held} — so {@link BuildingFactory#restore} always rebuilds a reloaded splitter with
 * {@link SortRule#ORE_FORWARD}, silently discarding whatever rule was actually in play.
 */
public final class Splitter implements Building {

    private final SortRule rule;
    private final Direction facing;
    private @Nullable Item held;

    public Splitter(SortRule rule, Direction facing) {
        this.rule = rule;
        this.facing = facing;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Splitter(SortRule rule, Direction facing, @Nullable Item held) {
        this(rule, facing);
        this.held = held;
    }

    @Override
    public boolean accept(World world, Item item) {
        if (held != null) {
            return false;
        }
        held = item;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (held == null) {
            return;
        }
        Direction direction = rule.forward(held) ? facing : facing.rotate();
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
        return Appearance.of(Sprite.SPLITTER);
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
    public BuildingMemento memento() {
        return new BuildingMemento.SplitterState(facing, held);
    }
}
