package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.OreLayout;

/**
 * Whether a {@link BuildingType} may be placed at a cell, beyond "the cell is free and in
 * bounds" — {@code World} already checks that universally, the same way, for every kind, so it
 * isn't this rule's job to repeat it. Exactly one kind needs anything more right now: a {@link
 * Miner} needs ore under it. Every other kind's rule is trivially "yes."
 *
 * <p>Collapses what used to be nine near-identical {@code World.place*} methods — seven of them
 * literally {@code return placeIfFree(TYPE, x, y, direction);} — down to one rule lookup plus one
 * {@link BuildingFactory#create} call in {@code World.place}. See P3-04, BUG_FIX_PROGRESS.md.
 */
@FunctionalInterface
public interface PlacementRule {

    /** Trivially satisfied — the rule for every kind except {@link Miner}. */
    PlacementRule ALWAYS = (x, y, oreLayout) -> true;

    /** A miner needs ore under the cell to do anything; placing it elsewhere would idle forever. */
    PlacementRule NEEDS_ORE = (x, y, oreLayout) -> oreLayout.hasOre(x, y);

    /** Whether {@code (x, y)} satisfies this rule, beyond the free+in-bounds check {@code World} already made. */
    boolean test(int x, int y, OreLayout oreLayout);

    /** The rule for {@code type} — {@link #NEEDS_ORE} for a miner, {@link #ALWAYS} for everything else. */
    static PlacementRule forType(BuildingType type) {
        return type == BuildingType.MINER ? NEEDS_ORE : ALWAYS;
    }
}
