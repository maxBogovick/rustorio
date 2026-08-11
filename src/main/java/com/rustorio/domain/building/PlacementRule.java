package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.VanillaItems;

/**
 * Whether a {@link BuildingType} may be placed at a cell, beyond "the cell is free and in
 * bounds" — {@code World} already checks that universally, the same way, for every kind, so it
 * isn't this rule's job to repeat it.
 *
 * <p><b>Owner decision (X-02, DEV_TASKS.md):</b> {@link com.rustorio.domain.VanillaItems#WATER}/{@link com.rustorio.domain.VanillaItems#ROCK} block
 * every kind except {@link UndergroundBelt} — the one building whose entire point is going UNDER
 * an obstacle rather than around it (§4.2 of the design audit: before terrain existed, a tunnel
 * solved no spatial problem at all, since there was nothing terrain-wise to route around). A
 * {@link Miner} additionally needs ore, on top of passable ground, since ore only matters if the
 * miner can physically stand there in the first place.
 *
 * <p>Collapses what used to be nine near-identical {@code World.place*} methods — seven of them
 * literally {@code return placeIfFree(TYPE, x, y, direction);} — down to one rule lookup plus one
 * {@link BuildingFactory#create} call in {@code World.place}. See P3-04, BUG_FIX_PROGRESS.md.
 *
 * <p>Which constant applies to which {@link BuildingType} is data now — see {@link
 * VanillaBuildings#registerAll} and {@link BuildingFactory#prototype} — not a {@code switch} here.
 */
@FunctionalInterface
public interface PlacementRule {

    /** Bypasses terrain entirely — only {@link UndergroundBelt} goes under an obstacle instead of needing it clear. */
    PlacementRule ALWAYS = (x, y, oreLayout) -> true;

    /** Every kind except a tunnel or a miner: needs passable ground, nothing more. */
    PlacementRule NEEDS_PASSABLE_TERRAIN = (x, y, oreLayout) -> oreLayout.isPassable(x, y);

    /** A miner needs passable ground AND ore under it to do anything; placing it elsewhere would idle forever. */
    PlacementRule NEEDS_ORE = (x, y, oreLayout) -> oreLayout.isPassable(x, y) && oreLayout.hasOre(x, y);

    /**
     * A pump stands on dry ground but has to REACH water — so it needs a {@link
     * com.rustorio.domain.VanillaItems#WATER} cell on one of its four sides. The same shape as {@link
     * #NEEDS_ORE} (something must be under, or next to, this cell for the building to do anything at
     * all), and the reason water finally has a use beyond being an obstacle to route around.
     *
     * <p>JSON and code mods should resolve placement rules through the {@code placementRules()}
     * registry ({@code BuildingJsonLoader}, {@link VanillaPlacementRules}) rather than binding
     * these interface constants: a balance mod that {@code update}s a named rule before freeze
     * only reaches buildings that peeked the registry id during their own registration. Constants
     * still seed the registry; they are the starting value, not a live late-bound handle.
     */
    PlacementRule ADJACENT_TO_WATER = (x, y, oreLayout) -> {
        if (!oreLayout.isPassable(x, y)) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (oreLayout.terrainAt(x + side.dx(), y + side.dy()).filter(VanillaItems.WATER::equals).isPresent()) {
                return true;
            }
        }
        return false;
    };

    /** Whether {@code (x, y)} satisfies this rule, beyond the free+in-bounds check {@code World} already made. */
    boolean test(int x, int y, OreLayout oreLayout);
}
