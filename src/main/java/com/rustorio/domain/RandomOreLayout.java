package com.rustorio.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * A second {@link OreLayout} implementation, alongside {@link PatchOreLayout}: same shape of map
 * (twelve iron/bronze patches plus four smaller coal patches, D-05), but scattered at positions
 * rolled from a seed instead of {@link PatchOreLayout}'s fixed coordinates — so a player isn't
 * stuck mining the exact same map every game. Deterministic per {@link OreLayout}'s contract:
 * patches are rolled once, at construction, from the given seed, and immediately baked into a
 * flat grid (P4-03, BUG_FIX_PROGRESS.md — same reasoning as {@link PatchOreLayout}), so {@link
 * #oreAt} always answers the same way, by index lookup, for the life of one instance. Since X-02
 * (DEV_TASKS.md), the same seeded roll also places water/rock {@link TerrainPatch} obstacles, ore
 * always winning any accidental overlap — see the constructor.
 */
public final class RandomOreLayout implements OreLayout {

    private static final int PATCH_COUNT = 12;
    private static final int BRONZE_PATCHES = 4;
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 4;

    /** Coal (D-05, DEV_TASKS.md) — smaller deposits than iron/bronze, same fixed radius {@link PatchOreLayout}'s coal patches use. */
    private static final int COAL_PATCHES = 4;
    private static final int COAL_RADIUS = 2;

    /** Obstacles (X-02, DEV_TASKS.md) — half water, half rock, same radius range as ore patches. */
    private static final int TERRAIN_PATCH_COUNT = 6;
    private static final int WATER_PATCHES = 3;

    private final long seed;
    private final int width;
    private final int height;
    private final @Nullable ItemType[] grid;
    /** Null means plain ground — see {@link OreLayout#terrainAt}. */
    private final @Nullable ItemType[] terrainGrid;
    /** Calls to {@link #extract} per cell so far, parallel to {@link #grid} — see {@link OreDepletion} (D-04). */
    private final int[] extractedCount;

    /** Roll a map of {@code width} x {@code height} cells from {@code seed}. */
    public RandomOreLayout(long seed, int width, int height) {
        this.seed = seed;
        this.width = width;
        this.height = height;
        Random random = new Random(seed);
        OrePatch[] patches = new OrePatch[PATCH_COUNT + COAL_PATCHES];
        for (int i = 0; i < PATCH_COUNT; i++) {
            ItemType ore = i < PATCH_COUNT - BRONZE_PATCHES ? VanillaItems.IRON_ORE : VanillaItems.BRONZE_ORE;
            int radius = fitRadius(MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1), width, height);
            int cx = radius + random.nextInt(width - 2 * radius);
            int cy = radius + random.nextInt(height - 2 * radius);
            patches[i] = new OrePatch(cx, cy, radius, ore);
        }
        // Coal, same seeded Random, right after iron/bronze so a furnace's two supply lines (ore
        // + fuel, D-05) both come from the same deterministic roll a player can actually learn.
        for (int i = 0; i < COAL_PATCHES; i++) {
            int radius = fitRadius(COAL_RADIUS, width, height);
            int cx = radius + random.nextInt(width - 2 * radius);
            int cy = radius + random.nextInt(height - 2 * radius);
            patches[PATCH_COUNT + i] = new OrePatch(cx, cy, radius, VanillaItems.COAL);
        }
        // Same seeded Random, continued — keeps "same seed -> same map" true for terrain too,
        // not just ore (see RandomOreLayoutTest.sameSeedYieldsTheExactSameMap).
        TerrainPatch[] terrainPatches = new TerrainPatch[TERRAIN_PATCH_COUNT];
        for (int i = 0; i < TERRAIN_PATCH_COUNT; i++) {
            ItemType terrain = i < WATER_PATCHES ? VanillaItems.WATER : VanillaItems.ROCK;
            int radius = fitRadius(MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1), width, height);
            int cx = radius + random.nextInt(width - 2 * radius);
            int cy = radius + random.nextInt(height - 2 * radius);
            terrainPatches[i] = new TerrainPatch(cx, cy, radius, terrain);
        }

        this.grid = new ItemType[width * height];
        this.extractedCount = new int[width * height];
        this.terrainGrid = new ItemType[width * height]; // all null = all plain ground, no fill needed

        // Bounding-box rasterization, not a full width×height scan checking every patch per cell
        // (code review finding) — same reasoning as PatchOreLayout's own constructor: each patch
        // touches only its own small circle of cells. Ore first, all of it, then terrain only into
        // still-ore-free cells — see rasterizeOre/rasterizeTerrain for how the original priority
        // ("first patch wins an overlap," "ore always wins over terrain") is preserved exactly.
        for (OrePatch patch : patches) {
            rasterizeOre(patch, width, height);
        }
        for (TerrainPatch patch : terrainPatches) {
            rasterizeTerrain(patch, width, height);
        }
    }

    /** Paints {@code patch} into {@link #grid}, touching only its own bounding box — see the constructor's own note. */
    private void rasterizeOre(OrePatch patch, int width, int height) {
        int minX = Math.max(0, patch.cx() - patch.radius());
        int maxX = Math.min(width - 1, patch.cx() + patch.radius());
        int minY = Math.max(0, patch.cy() - patch.radius());
        int maxY = Math.min(height - 1, patch.cy() + patch.radius());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (patch.contains(x, y)) {
                    int index = y * width + x;
                    if (grid[index] == null) { // first patch in roll order wins an overlap
                        grid[index] = patch.ore();
                    }
                }
            }
        }
    }

    /** Paints {@code patch} into {@link #terrainGrid}, skipping any cell ore already claimed — see the constructor's own note. */
    private void rasterizeTerrain(TerrainPatch patch, int width, int height) {
        int minX = Math.max(0, patch.cx() - patch.radius());
        int maxX = Math.min(width - 1, patch.cx() + patch.radius());
        int minY = Math.max(0, patch.cy() - patch.radius());
        int maxY = Math.min(height - 1, patch.cy() + patch.radius());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * width + x;
                if (grid[index] == null && patch.contains(x, y)) { // ore always wins — never paint terrain over an ore cell
                    terrainGrid[index] = patch.terrain();
                }
            }
        }
    }

    /**
     * {@code rolled}, shrunk until a patch of that radius fits inside a {@code width}×{@code height}
     * map with room for a centre (N9, NEW_BUGS_PROGRESS.md).
     *
     * <p>The centre used to be rolled as {@code radius + nextInt(Math.max(1, width - 2 * radius))}.
     * That {@code Math.max(1, …)} kept {@code nextInt} from throwing on a map narrower than the
     * patch, but at the cost of pinning the centre at exactly {@code radius} — past the far edge for
     * such a map, so the patch came out clipped, off-centre, or (on the smallest maps) entirely off
     * the grid, leaving a map with no ore on it at all. Shrinking the patch instead keeps the roll
     * meaning what it says at every size, and changes nothing for any map at least {@code 2 *
     * MAX_RADIUS + 1} cells across — which every real one is ({@code PatchOreLayout.STANDARD_WIDTH}
     * is 256).
     */
    private static int fitRadius(int rolled, int width, int height) {
        int room = (Math.min(width, height) - 1) / 2;
        return Math.max(0, Math.min(rolled, room));
    }

    @Override
    public Optional<ItemType> oreAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Optional.empty();
        }
        return Optional.ofNullable(grid[y * width + x]);
    }

    @Override
    public Optional<ItemType> extract(int x, int y) {
        Optional<ItemType> ore = oreAt(x, y);
        if (ore.isEmpty()) {
            return Optional.empty();
        }
        int index = y * width + x;
        boolean yields = OreDepletion.yields(extractedCount[index]++);
        return yields ? ore : Optional.empty();
    }

    @Override
    public OreLayoutId id() {
        return new OreLayoutId("random", seed, width, height);
    }

    @Override
    public Map<Integer, Integer> depletionSnapshot() {
        Map<Integer, Integer> snapshot = new HashMap<>();
        for (int index = 0; index < extractedCount.length; index++) {
            if (extractedCount[index] != 0) {
                snapshot.put(index, extractedCount[index]);
            }
        }
        return snapshot;
    }

    @Override
    public void restoreDepletion(Map<Integer, Integer> snapshot) {
        Arrays.fill(extractedCount, 0);
        for (Map.Entry<Integer, Integer> entry : snapshot.entrySet()) {
            extractedCount[entry.getKey()] = entry.getValue();
        }
    }

    @Override
    public Optional<ItemType> terrainAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Optional.of(VanillaItems.ROCK); // fail closed — off the generated grid entirely, never buildable
        }
        return Optional.ofNullable(terrainGrid[y * width + x]);
    }
}
