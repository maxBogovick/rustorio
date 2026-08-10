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
 * <p><b>What a technology does is still mostly the game's own code, not data.</b> {@code Chest}
 * still asks whether {@code BIG_BUFFER} is unlocked by name — a mod's own technology shows up in
 * the tree, costs points and unlocks, but has no effect on buffer size until some code reads it. The
 * "faster" family is the one exception: {@code Miner}/{@code Furnace}/{@code Lab} read a {@code
 * speedTech} trait off their own {@code BuildingPrototype} rather than a name, so a JSON-only mod
 * CAN give one of those archetypes its own speed-gating technology — see {@code
 * VanillaTraits#SPEED_TECH}. Every other per-technology effect still needs a jar mod (it knows its
 * own id; a JSON-only one has no code to read anything with). A general data-described effect
 * system is a separate design question, and a product decision rather than a mechanical one.
 *
 * <p>{@code prerequisites} are ids rather than resolved {@code TechType}s: a mod may name a
 * technology registered by a mod loaded later in the same round, and a record holding resolved
 * references could not be built until every one of them existed. {@code Research} resolves them
 * when it checks, which is also what makes a cycle detectable rather than unrepresentable — the
 * enum made cycles impossible by construction (a constant can only name earlier constants), and
 * that guarantee is genuinely lost here, so {@link #prerequisites} is checked for cycles at load
 * time instead (see {@code ModLoader}).
 */
public record TechType(ContentId id, String label, int cost, List<ContentId> prerequisites)
        implements Comparable<TechType> {

    public TechType {
        if (cost <= 0) {
            throw new IllegalArgumentException("tech '" + id + "' must cost more than zero points: " + cost);
        }
        if (prerequisites.contains(id)) {
            throw new IllegalArgumentException("tech '" + id + "' lists itself as its own prerequisite");
        }
        prerequisites = List.copyOf(prerequisites);
    }

    /** A technology with no prerequisites — a root of the tree. */
    public TechType(ContentId id, String label, int cost) {
        this(id, label, cost, List.of());
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
