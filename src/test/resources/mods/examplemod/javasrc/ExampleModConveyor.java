package com.examplemod.jarmod;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BeltSegment;
import com.rustorio.domain.building.BeltState;
import com.rustorio.domain.building.SettlesEachTick;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.TransportNode;
import java.util.Objects;
import java.util.Optional;

/**
 * A transport node written entirely outside com.rustorio.domain.building, compiled into this
 * mod's own real .jar - moves one item toward its own direction per tick (same as the vanilla
 * Belt) and fuses into the same BeltSegment cascade a vanilla belt joins.
 */
final class ExampleModConveyor implements Building, TransportNode, SettlesEachTick {

    private final ContentId prototypeId;
    private final Direction direction;
    private ItemType held;
    private BeltSegment segment;
    private boolean arrivedThisTick;

    ExampleModConveyor(ContentId prototypeId, Direction direction) {
        this.prototypeId = prototypeId;
        this.direction = direction;
    }

    ExampleModConveyor(ContentId prototypeId, Direction direction, ItemType held) {
        this.prototypeId = prototypeId;
        this.direction = direction;
        this.held = held;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public void joinSegment(BeltSegment segment) {
        this.segment = segment;
    }

    @Override
    public BeltSegment segment() {
        return Objects.requireNonNull(segment);
    }

    @Override
    public void leaveSegment() {
        if (segment != null) {
            segment.remove(this);
        }
    }

    @Override
    public ItemType held() {
        return held;
    }

    @Override
    public void setHeld(ItemType item) {
        held = item;
    }

    @Override
    public void clearHeld() {
        held = null;
    }

    @Override
    public boolean arrivedThisTick() {
        return arrivedThisTick;
    }

    @Override
    public void clearArrivalMark() {
        arrivedThisTick = false;
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

    @Override
    public void tick(TickContext world, int x, int y) {
        tickSegment(world, x, y);
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Appearance appearance() {
        return held == null ? Appearance.of(VanillaSprites.BELT_EMPTY) : Appearance.of(VanillaSprites.BELT_FULL);
    }

    @Override
    public BuildingType type() {
        return BuildingType.BELT;
    }

    @Override
    public ContentId prototypeId() {
        return prototypeId;
    }

    @Override
    public boolean prefersDescendingTick() {
        return direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    @Override
    public BeltState state() {
        return new BeltState(direction, held);
    }
}
