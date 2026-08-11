package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.List;

/**
 * A global technology: costs research points, once unlocked applies everywhere on the map (as
 * opposed to a per-building {@code speedLevel} upgrade).
 *
 * <p>Registered content addressed by {@link ContentId}, not an {@code enum} constant. It used to be
 * one, which meant the research tree was the single part of the game a mod could not extend at all:
 * a mod cannot add an enum constant, so it could not add a technology, could not ask whether one
 * was unlocked, and could not price one. Now a mod registers a {@code TechType} like any other
 * content.
 *
 * <p>{@code effects} lists {@link com.rustorio.api.mod.TechEffect} ids granted while this tech is
 * unlocked — buildings ask {@link ResearchView#hasEffect}, not a hardcoded tech name. The older
 * {@code speedTech} trait / {@code fasterIfUnlocked(techId)} path still works; effects are the
 * open, data-addressable door on top. An empty list means "tree node only" (UI + cost), same as
 * before effects existed.
 *
 * <p>{@code prerequisites} are ids rather than resolved {@code TechType}s: a mod may name a
 * technology registered by a mod loaded later in the same round, and a record holding resolved
 * references could not be built until every one of them existed. {@code Research} resolves them
 * when it checks, which is also what makes a cycle detectable rather than unrepresentable — the
 * enum made cycles impossible by construction (a constant can only name earlier constants), and
 * that guarantee is genuinely lost here, so {@link #prerequisites} is checked for cycles at load
 * time instead (see {@code ModLoader}).
 */
public record TechType(ContentId id, String label, int cost, List<ContentId> prerequisites, List<ContentId> effects)
        implements Comparable<TechType> {

    public TechType {
        if (cost <= 0) {
            throw new IllegalArgumentException("tech '" + id + "' must cost more than zero points: " + cost);
        }
        if (prerequisites.contains(id)) {
            throw new IllegalArgumentException("tech '" + id + "' lists itself as its own prerequisite");
        }
        prerequisites = List.copyOf(prerequisites);
        effects = List.copyOf(effects);
    }

    /** A technology with prerequisites but no effects — the shape JSON used before effects existed. */
    public TechType(ContentId id, String label, int cost, List<ContentId> prerequisites) {
        this(id, label, cost, prerequisites, List.of());
    }

    /** A technology with no prerequisites — a root of the tree. */
    public TechType(ContentId id, String label, int cost) {
        this(id, label, cost, List.of(), List.of());
    }

    /**
     * By {@link #id}, so a tech tree drawn from a registry lists the same rows in the same order on
     * every run. Consistent with {@code equals} in the sense that matters here — two techs with
     * equal ids are the same content and cannot both be registered — but NOT in the strict
     * {@code compareTo == 0} implies {@code equals} sense: same id with a different cost compares
     * equal and is not {@code equals}. That combination never reaches a sorted collection, because
     * a {@code Registry} refuses the second registration under an id it already holds.
     */
    @Override
    public int compareTo(TechType other) {
        return id.compareTo(other.id);
    }
}
