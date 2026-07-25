package com.rustorio.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Research progress: accumulated points and the set of unlocked {@link Tech}. Unlocking is
 * automatic — {@link #addPoints} opens every technology whose cost is already covered, in
 * ascending cost order, with no separate "choose what to research" screen.
 */
public final class Research implements ResearchView {

    private int points;
    private final Set<Tech> unlocked = EnumSet.noneOf(Tech.class);

    @Override
    public int points() {
        return points;
    }

    /** Currently unlocked technologies — defensively copied. */
    @Override
    public Set<Tech> unlocked() {
        return EnumSet.copyOf(unlocked);
    }

    @Override
    public boolean isUnlocked(Tech tech) {
        return unlocked.contains(tech);
    }

    /** {@code baseTime} halved (floor 1) if {@code tech} is unlocked, otherwise unchanged. */
    @Override
    public int fasterIfUnlocked(Tech tech, int baseTime) {
        return isUnlocked(tech) ? Math.max(1, baseTime / 2) : baseTime;
    }

    /** {@code baseCapacity} doubled if {@code tech} is unlocked, otherwise unchanged. */
    @Override
    public int biggerIfUnlocked(Tech tech, int baseCapacity) {
        return isUnlocked(tech) ? baseCapacity * 2 : baseCapacity;
    }

    /** Add points (called once per finished lab batch) and unlock whatever is now affordable. */
    public void addPoints(int amount) {
        points += amount;
        for (Tech tech : Tech.values()) {
            if (points >= tech.cost()) {
                unlocked.add(tech);
            }
        }
    }

    /** Reset to a fresh game's starting state. */
    public void clear() {
        points = 0;
        unlocked.clear();
    }

    /** Immutable point-in-time snapshot for persistence (Memento pattern) — see {@code JsonSaveRepository}. */
    public record Snapshot(int points, Set<Tech> unlocked) {
        public Snapshot {
            unlocked = EnumSet.copyOf(unlocked.isEmpty() ? EnumSet.noneOf(Tech.class) : unlocked);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(points, unlocked());
    }

    public void restore(Snapshot snapshot) {
        clear();
        points = snapshot.points();
        unlocked.addAll(snapshot.unlocked());
    }
}
