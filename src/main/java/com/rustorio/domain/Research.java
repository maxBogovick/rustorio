package com.rustorio.domain;

import java.util.Collections;
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

    /**
     * Currently unlocked technologies — an unmodifiable view, not a copy (P4-05,
     * BUG_FIX_PROGRESS.md): {@code HudRenderer} calls this once a frame, and {@code
     * EnumSet.copyOf} allocated a fresh set every single time for no reason nobody ever mutates
     * through the returned reference (callers only read). {@link #snapshot()} still makes a REAL,
     * independent copy — {@code Snapshot}'s compact constructor does that regardless of what's
     * passed in, so wrapping instead of copying here doesn't weaken that guarantee.
     */
    @Override
    public Set<Tech> unlocked() {
        return Collections.unmodifiableSet(unlocked);
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
            // EnumSet.copyOf refuses an empty non-EnumSet Set (it can't infer the element type
            // from zero elements) — build an empty EnumSet directly instead of copying in that case.
            var copy = EnumSet.noneOf(Tech.class);
            copy.addAll(unlocked);
            unlocked = copy;
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
