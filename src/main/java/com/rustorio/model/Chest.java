package com.rustorio.model;

import com.rustorio.*;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// model/Chest.java
public final class Chest implements Building {

    private List<Item> items = new ArrayList<>();
    private int count;
    public void add(Item item) {
        this.items.add(item);
    }

    public boolean accept(Item item) {
        if (items.size() >= 1000) { return false; }
        items.add(item);
        return true;
    }

    public Appearance appearance() { return Appearance.of(Sprite.CHEST, count()); }

    public int count() {
        count = this.items.size();
        return count;
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.empty();
    }

    public void tick(World world, int x, int y) {}

    @Override public BuildingType type() { return BuildingType.CHEST; }
    @Override public String save()       { return Integer.toString(count()); }

    public static Chest load(String data) {
        Chest chest = new Chest();
        chest.count = Integer.parseInt(data);
        return chest;
    }
}