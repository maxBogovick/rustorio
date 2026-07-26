package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;

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
    public boolean accept(TickContext world, Item item) {
        count++;
        return true;
    }

    public int count() {
        return count;
    }

    @Override
    public Appearance appearance() {
        return count > 0 ? Appearance.of(Sprite.CHEST, count) : Appearance.of(Sprite.CHEST);
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
