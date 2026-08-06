package com.rustorio.model;

import com.rustorio.Appearance;
import com.rustorio.BuildingType;
import com.rustorio.SortRule;
import com.rustorio.World;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.Optional;

public final class Splitter implements Building{
    private final SortRule rule;
    private Item held;

    public Splitter(SortRule rule) {
        this.rule = rule;
    }

    @Override
    public boolean accept(Item item) {
        if (held != null) return false;
        held = item;
        return true;
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(Direction.EAST);
    }

    @Override
    public Appearance appearance() {
        return null;
    }

    @Override
    public void tick(World world, int x, int y) {
        if(held == null) return;
        boolean forward = rule.forward(held);
        int dx = forward ? x+1 : x;
        int dy = forward ? y : y+1;
        if (world.offerForward(dx, dy, held)) held = null;
    }

    @Override
    public BuildingType type() {
        return null;
    }

    @Override
    public String save() {
        return "splitter";
    }
}
