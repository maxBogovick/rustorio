package com.rustorio.domain;

/**
 * A circular terrain obstacle — the counterpart to {@link OrePatch}, kept as its own tiny record
 * rather than sharing one with it: an ore patch is something a miner extracts from, a terrain patch
 * is something nothing can be built on, and the two answer different questions even now that both
 * name an {@link ItemType}.
 *
 * <p>{@code terrain} used to be a closed {@code Terrain} enum with exactly {@code WATER} and
 * {@code ROCK} in it, so a mod could not add a third kind of obstacle without a change to the
 * engine. It is content now, addressed like every other piece of content: a map names an item, and
 * the cells that patch covers report that item as what lies on them. {@code GROUND} did not survive
 * the change and did not need to — it never meant a patch, only the absence of one, which is now
 * spelled as exactly that (see {@link OreLayout#terrainAt}).
 *
 * <p>Every terrain patch blocks building, whatever it names — the rule the two vanilla obstacles
 * already followed, unchanged. Passability is deliberately NOT a field on the referenced item: an
 * item that a cell can be built on would be a terrain patch that isn't an obstacle, which is the
 * same thing as no patch at all.
 *
 * <p>Public for the same reason {@link OrePatch} is: {@code com.rustorio.mod.MapJsonLoader}
 * constructs these directly from a mod's {@code content/maps/*.json} to build an {@link
 * AuthoredMap}.
 */
public record TerrainPatch(int cx, int cy, int radius, ItemType terrain) {

    boolean contains(int x, int y) {
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}
