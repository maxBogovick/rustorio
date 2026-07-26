package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
import org.jspecify.annotations.Nullable;

/**
 * Spends finished goods on research points instead of passing them along — the chain's terminus
 * rather than another link in it. Accepts every {@link Item#isResearchGrade} item alike (P2-08,
 * BUG_FIX_PROGRESS.md — the list of which items qualify lives on {@link Item} itself, not here); a
 * pricier item earns no bonus points (a deliberate simplification — a per-item price would need a
 * queue instead of a plain counter, not worth the risk for a secondary detail).
 *
 * <p>Matches {@link Furnace}'s {@code ProcessTimer} policy (P2-05, BUG_FIX_PROGRESS.md): the timer
 * is created lazily, on the first {@link #accept}, from whatever {@link Tech#FAST_LAB} state holds
 * at that moment — not eagerly at construction. A lab built after the tech is already unlocked
 * must not cook its first batch at the un-halved rate just because nobody had fed it yet.
 */
public final class Lab implements Building {

    private static final int BUFFER_MAX = 5;
    private static final int RESEARCH_TIME = 10;

    private int buffer;
    private @Nullable ProcessTimer timer;

    public Lab() {
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Lab(int buffer, int cooldown) {
        this.buffer = buffer;
        if (buffer > 0) {
            this.timer = new ProcessTimer(cooldown);
        }
    }

    @Override
    public boolean accept(TickContext world, Item item) {
        if (!item.isResearchGrade() || buffer >= BUFFER_MAX) {
            return false;
        }
        if (timer == null) {
            timer = new ProcessTimer(effectiveTime(world));
        }
        buffer++;
        return true;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        ProcessTimer current = timer;
        if (buffer == 0 || current == null) {
            return;
        }
        if (!current.tick(effectiveTime(world))) {
            return;
        }
        buffer--;
        world.addResearchPoints(1);
    }

    private static int effectiveTime(TickContext world) {
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
        ProcessTimer current = timer;
        return new BuildingMemento.LabState(buffer, current == null ? 0 : current.cooldown());
    }
}
