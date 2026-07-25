package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One half of a tunnel pair that carries an item under an obstacle, bypassing the usual
 * "offer to the neighbor ahead" hand-off. The {@link Kind#IN} half takes from an ordinary
 * neighbor behind it and, every tick, searches ahead along its {@link Direction} for the nearest
 * same-orientation {@link Kind#OUT} within {@link #MAX_RANGE} tiles, handing the item to it
 * directly. The {@code OUT} half then pushes forward like a plain {@link Belt}.
 */
public final class UndergroundBelt implements Building {

    public enum Kind {
        IN, OUT
    }

    private static final int MAX_RANGE = 4;

    private final Kind kind;
    private final Direction direction;
    private @Nullable Item held;

    public UndergroundBelt(Kind kind, Direction direction) {
        this.kind = kind;
        this.direction = direction;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    UndergroundBelt(Kind kind, Direction direction, @Nullable Item held) {
        this(kind, direction);
        this.held = held;
    }

    @Override
    public boolean accept(World world, Item item) {
        if (kind != Kind.IN || held != null) {
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
        if (kind == Kind.IN) {
            tickIn(world, x, y);
        } else {
            tickOut(world, x, y);
        }
    }

    private void tickIn(World world, int x, int y) {
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
    public Optional<UndergroundBelt> findPartner(World world, int x, int y) {
        if (kind != Kind.IN) {
            return Optional.empty();
        }
        int dx = direction.dx();
        int dy = direction.dy();
        for (int step = 1; step <= effectiveRange(world); step++) {
            Optional<Building> candidate = world.peek(x + dx * step, y + dy * step);
            if (candidate.isPresent()
                    && Building.unwrap(candidate.get()) instanceof UndergroundBelt other
                    && other.kind == Kind.OUT
                    && other.direction == direction) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    private static int effectiveRange(World world) {
        return world.research().isUnlocked(Tech.LONG_TUNNEL) ? MAX_RANGE * 2 : MAX_RANGE;
    }

    private void tickOut(World world, int x, int y) {
        Item cargo = held;
        if (cargo == null) {
            return;
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), cargo)) {
            held = null;
        }
    }

    @Override
    public Optional<Item> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(kind == Kind.IN ? Sprite.UNDERGROUND_IN : Sprite.UNDERGROUND_OUT);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public BuildingType type() {
        return kind == Kind.IN ? BuildingType.UNDERGROUND_IN : BuildingType.UNDERGROUND_OUT;
    }

    @Override
    public boolean prefersDescendingTick() {
        return kind == Kind.IN || direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.UndergroundBeltState(kind, direction, held);
    }
}
