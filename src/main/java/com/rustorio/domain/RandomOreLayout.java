package com.rustorio.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A second {@link OreLayout} implementation, alongside {@link PatchOreLayout}: same shape of map
 * (twelve circular patches, two-thirds iron, one-third bronze), but scattered at positions rolled
 * from a seed instead of {@link PatchOreLayout}'s fixed coordinates — so a player isn't stuck
 * mining the exact same map every game. Deterministic per {@link OreLayout}'s contract: patches
 * are rolled once, at construction, from the given seed, so {@link #oreAt} always answers the
 * same way for the life of one instance.
 */
public final class RandomOreLayout implements OreLayout {

    private record Patch(int cx, int cy, int radius, Item ore) {
        boolean contains(int x, int y) {
            int dx = x - cx;
            int dy = y - cy;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private static final int PATCH_COUNT = 12;
    private static final int BRONZE_PATCHES = 4;
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 4;

    private final List<Patch> patches;

    /** Roll a map of {@code width} x {@code height} cells from {@code seed}. */
    public RandomOreLayout(long seed, int width, int height) {
        Random random = new Random(seed);
        List<Patch> rolled = new ArrayList<>(PATCH_COUNT);
        for (int i = 0; i < PATCH_COUNT; i++) {
            Item ore = i < PATCH_COUNT - BRONZE_PATCHES ? Item.IRON_ORE : Item.BRONZE_ORE;
            int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
            int cx = radius + random.nextInt(Math.max(1, width - 2 * radius));
            int cy = radius + random.nextInt(Math.max(1, height - 2 * radius));
            rolled.add(new Patch(cx, cy, radius, ore));
        }
        this.patches = List.copyOf(rolled);
    }

    @Override
    public Optional<Item> oreAt(int x, int y) {
        for (Patch patch : patches) {
            if (patch.contains(x, y)) {
                return Optional.of(patch.ore());
            }
        }
        return Optional.empty();
    }
}
