package com.rustorio.model;

import com.rustorio.*;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.Optional;

public final class Furnace implements Building {

    private static final int SMELT_TIME = 5;   // тиков на одну пластину
    private static final int BUFFER_MAX = 5; // max size for in(out)put

    private int oreBuffer;
    private int cooldown = SMELT_TIME;

    @Override
    public Optional<Direction> direction() {
        return Optional.empty();
    }

    @Override
    public boolean sameKind(Building other) {
        return Building.super.sameKind(other);
    }

    @Override
    public void tick(World world, int x, int y) {
        if (oreBuffer == 0) return;
        if (--cooldown > 0) return;
        cooldown = SMELT_TIME;//for the next resource pack
        oreBuffer -= 1;
        world.offerToNeighbor(x, y, Item.IRON_PLATE);
    }

    public Appearance appearance() {
        return oreBuffer > 0
                ? Appearance.of(Sprite.FURNACE_HOT, oreBuffer)
                : Appearance.of(Sprite.FURNACE_COLD);
    }

    public boolean accept(Item item) {
        if (item != Item.IRON_ORE || oreBuffer >= BUFFER_MAX) { return false; }
        oreBuffer+=1;
        return true;
    }

    public int oreBuffer() { return oreBuffer; }

    @Override public BuildingType type() { return BuildingType.FURNACE; }
    @Override public String save()       { return oreBuffer + " " + cooldown; }

    public static Furnace load(String data) {
        String[] fields = data.split(" ");
        Furnace furnace = new Furnace();
        furnace.oreBuffer = Integer.parseInt(fields[0]);
        furnace.cooldown = Integer.parseInt(fields[1]);
        return furnace;
    }

}
