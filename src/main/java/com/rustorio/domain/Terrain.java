package com.rustorio.domain;

/**
 * What kind of ground lies under a cell — orthogonal to {@link ItemType} ore (a cell reports both:
 * see {@link OreLayout#terrainAt}). Only {@link #GROUND} is buildable; {@link #WATER} and {@link
 * #ROCK} are the obstacles X-02 (DEV_TASKS.md) introduces, giving a tunnel (the only building
 * {@code PlacementRule} lets ignore terrain) something to actually be needed for — before this,
 * every cell was equally buildable, so there was no spatial problem left for a tunnel to solve.
 */
public enum Terrain {
    GROUND,
    WATER,
    ROCK;

    /** Whether a building may stand here at all, terrain-wise. */
    public boolean isPassable() {
        return this == GROUND;
    }
}
