package com.rustorio.model;

import com.rustorio.Appearance;
import com.rustorio.BuildingType;
import com.rustorio.Sprite;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.World;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Optional;

public sealed class Belt implements Building permits Splitter {

    private Item[] held = new Item[2];

    private int sizeOfHeld;

    @Override
    public boolean accept(Item item) {
        if (sizeOfHeld == 2) return false;
        else if (sizeOfHeld < 4 && sizeOfHeld >= 0) {
            held[0] = item;
            sizeOfHeld++;
            return true;
        }
        return false;
    }

    @Override
    public void tick(World world, int x, int y) {
        int count = 0;
        while(count < sizeOfHeld) {
            if (sizeOfHeld == 0) return;
            if (world.offerForward(x+1, y, held[count])) {
                held[count] = null;
            }
            count++;
        }
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(Direction.EAST);
    }

    @Override
    public Appearance appearance() {
        return held == null ? Appearance.of(Sprite.BELT_EMPTY) : Appearance.of(Sprite.BELT_FULL);
    }

    @Override
    public BuildingType type() { return BuildingType.BELT; }

    @Override
    public String save() {
        if (held == null) return null;
        for (int i=0; i<sizeOfHeld; i++) {
            if (held[i] != null) {
                return held[i].name;
            }
        }
        return null;
    }

    public static Belt load(String data) {
        Belt belt = new Belt();
        Item[] held = new Item[2];

        if (!data.equals("-")) {
            for (Item i : held) {
                i = Item.valueOf(data);
            }
            belt.held = held;
        }
        return belt;
    }
}
