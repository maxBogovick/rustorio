package com.rustorio.domain;

import java.util.Optional;

/**
 * Strategy pattern: where ore lies on the map and of which kind. {@link World} depends on this
 * interface, not on {@link PatchOreLayout} directly — a test can hand it a fixed, tiny layout
 * instead of the real map, and a future "random seed" world generator is a second implementation,
 * not a change to {@code World} or {@code Miner}.
 */
public interface OreLayout {

    /** Which ore (if any) lies under cell {@code (x, y)}. Must be deterministic. */
    Optional<Item> oreAt(int x, int y);

    default boolean hasOre(int x, int y) {
        return oreAt(x, y).isPresent();
    }

    /** Which map this is — see {@link OreLayoutId} for why a save needs to know. */
    OreLayoutId id();
}
