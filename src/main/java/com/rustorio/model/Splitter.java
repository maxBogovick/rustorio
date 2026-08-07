package com.rustorio.model;

import com.rustorio.Appearance;
import com.rustorio.BuildingType;
import com.rustorio.SortRule;
import com.rustorio.World;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.Optional;
import java.util.function.Supplier;

public final class Splitter extends Belt implements Building{
    private final SortRule rule;
    private Supplier<Item> held;

    public Splitter(SortRule rule) {
        this.rule = rule;
    }

    @Override
    public void tick(World world, int x, int y) {
        if(held == null) return;
        boolean forward = rule.forward(held);
        int dx = forward ? x+1 : x;
        int dy = forward ? y : y+1;
        if (world.offerForward(dx, dy, held.get())) held = null;
    }

    @Override
    public BuildingType type() {
        return BuildingType.SPLITTER;
    }

    @Override
    public String save() {
        return "splitter";
    }

    public static Splitter load(String data, SortRule rule) {
        Splitter splitter = new Splitter(rule);
        Supplier<Item> held = new Supplier<Item>() {
            @Override
            public Item get() {
                return Item.valueOf(data);
            }
        };

        if (!data.equals("-")) {
                held.get();
            splitter.held = held;
        }
        return splitter;
    }
}
