package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.world.World;

/** Sits on the map and accumulates whatever neighbors hand it — no sorting by kind yet. */
public final class Chest implements Building {

    private int count;

    public Chest() {
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Chest(int count) {
        this.count = count;
    }

    @Override
    public boolean accept(World world, Item item) {
        count++;
        return true;
    }

    public int count() {
        return count;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.CHEST, count);
    }

    @Override
    public BuildingType type() {
        return BuildingType.CHEST;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.ChestState(count);
    }
}
