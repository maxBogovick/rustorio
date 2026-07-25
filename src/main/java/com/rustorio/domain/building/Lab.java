package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;

/**
 * Spends finished goods on research points instead of passing them along — the chain's terminus
 * rather than another link in it. Accepts every top-tier item ({@link Item#GEAR}, {@link
 * Item#MECHANISM}, {@link Item#ENGINE}, {@link Item#CHASSIS}, {@link Item#ALLOY_GEAR}) alike; a
 * pricier item earns no bonus points (a deliberate simplification — a per-item price would need a
 * queue instead of a plain counter, not worth the risk for a secondary detail).
 */
public final class Lab implements Building {

    private static final int BUFFER_MAX = 5;
    private static final int RESEARCH_TIME = 10;

    private int buffer;
    private final ProcessTimer timer;

    public Lab() {
        this.timer = new ProcessTimer(RESEARCH_TIME);
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Lab(int buffer, int cooldown) {
        this();
        this.buffer = buffer;
        this.timer.restore(cooldown);
    }

    @Override
    public boolean accept(World world, Item item) {
        boolean known = item == Item.GEAR || item == Item.MECHANISM || item == Item.ENGINE
                || item == Item.CHASSIS || item == Item.ALLOY_GEAR;
        if (!known || buffer >= BUFFER_MAX) {
            return false;
        }
        buffer++;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (buffer == 0) {
            return;
        }
        if (!timer.tick(effectiveTime(world))) {
            return;
        }
        buffer--;
        world.addResearchPoints(1);
    }

    private static int effectiveTime(World world) {
        return world.research().fasterIfUnlocked(Tech.FAST_LAB, RESEARCH_TIME);
    }

    @Override
    public Appearance appearance() {
        return buffer > 0 ? Appearance.of(Sprite.LAB, buffer) : Appearance.of(Sprite.LAB);
    }

    @Override
    public BuildingType type() {
        return BuildingType.LAB;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.LabState(buffer, timer.cooldown());
    }
}
