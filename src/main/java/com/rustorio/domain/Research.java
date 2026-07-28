package com.rustorio.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Research progress: accumulated points and the set of unlocked {@link Tech}.
 *
 * <p><b>Owner decision (P-02, DEV_TASKS.md):</b> unlocking used to be automatic — {@code addPoints}
 * opened every technology whose cost was already covered, in ascending cost order, with no
 * separate "choose what to research" screen (§2.4 of the design audit: this left the player no
 * agency at all — accumulate enough points and everything unlocks itself). Now {@link #addPoints}
 * only accumulates; {@link #unlock} is the explicit, player-triggered action that actually SPENDS
 * a tech's cost out of the pool. Spending, not just gating the same never-shrinking total behind a
 * button press, is deliberate: if points were never actually deducted, the eventual end state
 * (every tech unlocked once lifetime points cross the sum of every cost) would be identical no
 * matter what the player chose or in what order — a button press with no real tradeoff behind it,
 * which wouldn't have fixed the audit's actual complaint. Spending means an early, avoidable choice
 * can leave a later, pricier tech permanently out of reach for that playthrough.
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

    /** Add points (called once per finished lab batch) — accumulation only, see the class javadoc for why unlocking moved to {@link #unlock}. */
    public void addPoints(int amount) {
        points += amount;
    }

    /**
     * Spend {@code tech}'s {@link Tech#cost()} out of the pool to unlock it — the player's
     * explicit choice (P-02, DEV_TASKS.md). Refuses, spending and unlocking nothing, unless BOTH
     * hold: enough points are banked, and every one of {@link Tech#prerequisites()} is already
     * unlocked. Already-unlocked also refuses (there's nothing left to spend on it) rather than
     * silently re-charging the player for a tech they already have.
     *
     * @return whether the tech was actually unlocked just now
     */
    public boolean unlock(Tech tech) {
        if (isUnlocked(tech) || points < tech.cost() || !unlocked.containsAll(tech.prerequisites())) {
            return false;
        }
        points -= tech.cost();
        unlocked.add(tech);
        return true;
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
            // Wrapped, not handed out bare (N5, NEW_BUGS_PROGRESS.md): copying on the way IN only
            // stops the caller's set from changing this snapshot later — the accessor still handed
            // back a live, mutable EnumSet anyone could add to or clear, which is exactly what a
            // point-in-time record must not allow. Same discipline as PlayerInventory.Snapshot.
            unlocked = Collections.unmodifiableSet(copy);
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
