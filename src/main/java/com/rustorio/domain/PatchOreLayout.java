package com.rustorio.domain;

import java.util.Optional;

/**
 * The game's real {@link OreLayout}: a fixed set of circular ore patches, the same map every
 * playthrough. Deterministic by construction — a cell's answer depends only on its coordinates,
 * never on call order, so {@link Miner} can ask again on every tick instead of caching the
 * answer itself.
 */
public final class PatchOreLayout implements OreLayout {

    private record Patch(int cx, int cy, int radius, Item ore) {
        boolean contains(int x, int y) {
            int dx = x - cx;
            int dy = y - cy;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private static final Patch[] PATCHES = {
            new Patch(6, 5, 3, Item.IRON_ORE), new Patch(9, 14, 3, Item.IRON_ORE),
            new Patch(25, 6, 4, Item.IRON_ORE), new Patch(28, 15, 3, Item.IRON_ORE),
            new Patch(52, 10, 4, Item.IRON_ORE), new Patch(74, 20, 3, Item.IRON_ORE),
            new Patch(45, 34, 4, Item.IRON_ORE), new Patch(14, 44, 3, Item.IRON_ORE),
            new Patch(60, 52, 4, Item.BRONZE_ORE), new Patch(84, 42, 3, Item.BRONZE_ORE),
            new Patch(33, 56, 3, Item.BRONZE_ORE), new Patch(88, 8, 3, Item.BRONZE_ORE),
    };

    private static final PatchOreLayout STANDARD = new PatchOreLayout();

    /** The game's built-in map — twelve patches, eight iron and four bronze. */
    public static PatchOreLayout standard() {
        return STANDARD;
    }

    @Override
    public Optional<Item> oreAt(int x, int y) {
        for (Patch patch : PATCHES) {
            if (patch.contains(x, y)) {
                return Optional.of(patch.ore());
            }
        }
        return Optional.empty();
    }
}
