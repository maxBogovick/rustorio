package com.rustorio.domain;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The game's real {@link OreLayout}: a fixed set of circular ore patches, the same map every
 * playthrough. Deterministic by construction — a cell's answer depends only on its coordinates,
 * never on call order, so {@link Miner} can ask again on every tick instead of caching the
 * answer itself.
 *
 * <p>The map is precomputed once, at construction, into a flat array indexed by cell (P4-03,
 * BUG_FIX_PROGRESS.md) — not scanned patch-by-patch on every {@link #oreAt} call. {@code
 * WorldRenderer} calls {@code oreAt} for every visible cell, every frame: at maximum zoom-out
 * that's the whole {@value #STANDARD_WIDTH}x{@value #STANDARD_HEIGHT} map, and a linear scan of
 * twelve patches per cell added up to roughly 74,000 {@code contains} checks a frame — the
 * dominant cost of drawing the ground layer.
 */
public final class PatchOreLayout implements OreLayout {

    /**
     * Mirrors {@code GfxConfig.GRID_W}/{@code GRID_H} (the {@code com.graphics} package) without
     * depending on it — the domain doesn't know the rendering layer's config exists. If the real
     * map size ever changes, this constant and {@code GfxConfig}'s must be updated together;
     * nothing enforces that automatically, but the two were never wired together in the first
     * place either.
     */
    private static final int STANDARD_WIDTH = 96;

    private static final int STANDARD_HEIGHT = 64;

    private static final OrePatch[] PATCHES = {
            new OrePatch(6, 5, 3, Item.IRON_ORE), new OrePatch(9, 14, 3, Item.IRON_ORE),
            new OrePatch(25, 6, 4, Item.IRON_ORE), new OrePatch(28, 15, 3, Item.IRON_ORE),
            new OrePatch(52, 10, 4, Item.IRON_ORE), new OrePatch(74, 20, 3, Item.IRON_ORE),
            new OrePatch(45, 34, 4, Item.IRON_ORE), new OrePatch(14, 44, 3, Item.IRON_ORE),
            new OrePatch(60, 52, 4, Item.BRONZE_ORE), new OrePatch(84, 42, 3, Item.BRONZE_ORE),
            new OrePatch(33, 56, 3, Item.BRONZE_ORE), new OrePatch(88, 8, 3, Item.BRONZE_ORE),
    };

    private static final PatchOreLayout STANDARD = new PatchOreLayout(STANDARD_WIDTH, STANDARD_HEIGHT);

    private final int width;
    private final int height;
    private final @Nullable Item[] grid;

    private PatchOreLayout(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new Item[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (OrePatch patch : PATCHES) {
                    if (patch.contains(x, y)) {
                        grid[y * width + x] = patch.ore();
                        break;
                    }
                }
            }
        }
    }

    /** The game's built-in map — twelve patches, eight iron and four bronze. */
    public static PatchOreLayout standard() {
        return STANDARD;
    }

    @Override
    public Optional<Item> oreAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Optional.empty();
        }
        return Optional.ofNullable(grid[y * width + x]);
    }

    /** Always the same fixed map — {@code seed}/{@code width}/{@code height} carry no meaning here. */
    @Override
    public OreLayoutId id() {
        return new OreLayoutId("patch", 0, 0, 0);
    }
}
