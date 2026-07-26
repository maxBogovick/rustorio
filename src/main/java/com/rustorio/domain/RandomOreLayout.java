package com.rustorio.domain;

import java.util.Optional;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * A second {@link OreLayout} implementation, alongside {@link PatchOreLayout}: same shape of map
 * (twelve circular patches, two-thirds iron, one-third bronze), but scattered at positions rolled
 * from a seed instead of {@link PatchOreLayout}'s fixed coordinates — so a player isn't stuck
 * mining the exact same map every game. Deterministic per {@link OreLayout}'s contract: patches
 * are rolled once, at construction, from the given seed, and immediately baked into a flat grid
 * (P4-03, BUG_FIX_PROGRESS.md — same reasoning as {@link PatchOreLayout}), so {@link #oreAt}
 * always answers the same way, by index lookup, for the life of one instance.
 */
public final class RandomOreLayout implements OreLayout {

    private static final int PATCH_COUNT = 12;
    private static final int BRONZE_PATCHES = 4;
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 4;

    private final long seed;
    private final int width;
    private final int height;
    private final @Nullable Item[] grid;

    /** Roll a map of {@code width} x {@code height} cells from {@code seed}. */
    public RandomOreLayout(long seed, int width, int height) {
        this.seed = seed;
        this.width = width;
        this.height = height;
        Random random = new Random(seed);
        OrePatch[] patches = new OrePatch[PATCH_COUNT];
        for (int i = 0; i < PATCH_COUNT; i++) {
            Item ore = i < PATCH_COUNT - BRONZE_PATCHES ? Item.IRON_ORE : Item.BRONZE_ORE;
            int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
            int cx = radius + random.nextInt(Math.max(1, width - 2 * radius));
            int cy = radius + random.nextInt(Math.max(1, height - 2 * radius));
            patches[i] = new OrePatch(cx, cy, radius, ore);
        }

        this.grid = new Item[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (OrePatch patch : patches) {
                    if (patch.contains(x, y)) {
                        grid[y * width + x] = patch.ore();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public Optional<Item> oreAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Optional.empty();
        }
        return Optional.ofNullable(grid[y * width + x]);
    }

    @Override
    public OreLayoutId id() {
        return new OreLayoutId("random", seed, width, height);
    }
}
