package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import com.rustorio.api.content.model.TechType;

/**
 * Research progress: accumulated points and the set of unlocked technologies.
 *
 * <p><b>Owner decision:</b> unlocking used to be automatic — {@code addPoints} opened every
 * technology whose cost was already covered, which left the player no agency at all. Now {@link
 * #addPoints} only accumulates; {@link #unlock} is the explicit, player-triggered action that
 * actually SPENDS a tech's cost out of the pool. Spending, not just gating the same never-shrinking
 * total behind a button press, is deliberate: if points were never deducted, the eventual end state
 * would be identical no matter what the player chose or in what order — a button press with no real
 * tradeoff behind it. Spending means an early, avoidable choice can leave a later, pricier tech
 * permanently out of reach for that playthrough.
 *
 * <p>Keyed by {@link ContentId} against an injected {@link Registry} of {@link TechType}, not by an
 * {@code enum} constant against a fixed list. The registry is a constructor argument for the same
 * reason {@code RecipeBook} is one: a game running mods researches through a different set than a
 * game running none, and no static field can be both.
 *
 * <p>{@link LinkedHashSet}, not {@code Set.of} or a hash set: {@link #unlocked()} is iterated by
 * the tech-tree panel and written to the save, and both have to come out the same on every run.
 *
 * <p>{@link #grantedEffects} is maintained incrementally on {@link #unlock}/{@link #restore}/
 * {@link #clear} so {@link #hasEffect} is a set lookup — buildings ask every accept/tick, and
 * scanning unlocked techs with {@code peek} would allocate on the hot path.
 */
public final class Research implements ResearchView {

    private final Registry<TechType> techs;
    private int points;
    private final Set<ContentId> unlocked = new LinkedHashSet<>();
    private final Set<ContentId> grantedEffects = new LinkedHashSet<>();

    public Research(Registry<TechType> techs) {
        this.techs = techs;
    }

    @Override
    public Registry<TechType> techs() {
        return techs;
    }

    @Override
    public int points() {
        return points;
    }

    /**
     * Currently unlocked technologies — an unmodifiable view, not a copy: the HUD calls this once a
     * frame, and copying allocated a fresh set every time for a caller that only reads. {@link
     * #snapshot()} still makes a REAL, independent copy.
     */
    @Override
    public Set<ContentId> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    @Override
    public boolean isUnlocked(ContentId tech) {
        return unlocked.contains(tech);
    }

    /** {@code baseTime} halved (floor 1) if {@code tech} is unlocked, otherwise unchanged. */
    @Override
    public int fasterIfUnlocked(ContentId tech, int baseTime) {
        return isUnlocked(tech) ? Math.max(1, baseTime / 2) : baseTime;
    }

    /**
     * {@code baseCapacity} doubled if {@code tech} is unlocked, otherwise unchanged.
     *
     * <p>Prefer {@link #hasEffect} for capacity/range bonuses mods can grant: this helper stays for
     * call sites that intentionally gate on a <em>specific technology id</em> rather than a named
     * effect.
     */
    @Override
    public int biggerIfUnlocked(ContentId tech, int baseCapacity) {
        return isUnlocked(tech) ? baseCapacity * 2 : baseCapacity;
    }

    @Override
    public boolean hasEffect(ContentId effect) {
        return grantedEffects.contains(effect);
    }

    /** Add points (called once per finished lab batch) — accumulation only, see the class javadoc for why unlocking moved to {@link #unlock}. */
    public void addPoints(int amount) {
        points += amount;
    }

    /**
     * Spend {@code tech}'s {@link TechType#cost()} out of the pool to unlock it — the player's
     * explicit choice. Refuses, spending and unlocking nothing, unless all three hold: the id names
     * a registered technology, enough points are banked, and every one of its prerequisites is
     * already unlocked. Already-unlocked also refuses, rather than silently re-charging the player
     * for a tech they already have.
     *
     * <p>An unregistered id refuses instead of throwing: a save can name a technology whose mod was
     * removed, and the tech-tree panel should go quiet about it rather than take the game down.
     *
     * @return whether the tech was actually unlocked just now
     */
    public boolean unlock(ContentId tech) {
        TechType type = techs.peek(tech).orElse(null);
        if (type == null || isUnlocked(tech) || points < type.cost() || !unlocked.containsAll(type.prerequisites())) {
            return false;
        }
        points -= type.cost();
        unlocked.add(tech);
        grantedEffects.addAll(type.effects());
        return true;
    }

    /** Reset to a fresh game's starting state. */
    public void clear() {
        points = 0;
        unlocked.clear();
        grantedEffects.clear();
    }

    /** Immutable point-in-time snapshot for persistence (Memento pattern) — see {@code JsonSaveRepository}. */
    public record Snapshot(int points, Set<ContentId> unlocked) {
        public Snapshot {
            // Copied on the way in AND wrapped on the way out: copying alone only stops the
            // caller's set from changing this snapshot later, while the accessor still handed back
            // a live, mutable set anyone could add to — which is exactly what a point-in-time
            // record must not allow. LinkedHashSet for the same save-order reason as the field.
            unlocked = Collections.unmodifiableSet(new LinkedHashSet<>(unlocked));
        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(points, unlocked);
    }

    /**
     * Overwrite progress wholesale from a save. Ids naming a technology this game no longer has
     * (its mod was removed) are dropped rather than kept: keeping one would let it come back out
     * in the next save and outlive the mod indefinitely, and nothing can act on it meanwhile.
     */
    public void restore(Snapshot snapshot) {
        clear();
        points = snapshot.points();
        for (ContentId id : snapshot.unlocked()) {
            TechType type = techs.peek(id).orElse(null);
            if (type != null) {
                unlocked.add(id);
                grantedEffects.addAll(type.effects());
            }
        }
    }
}
