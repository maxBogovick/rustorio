package com.rustorio.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
 * sixteen patches per cell would add up to roughly 1,048,000 {@code contains} checks a frame if it
 * weren't precomputed — the dominant cost of drawing the ground layer.
 */
public final class PatchOreLayout implements OreLayout {

    /**
     * The one place the map's size is actually decided (X-04, DEV_TASKS.md) — {@code
     * GfxConfig.GRID_W}/{@code GRID_H} (the {@code com.graphics} package) reference these directly
     * rather than repeating the numbers, which is what used to let the two silently disagree
     * (nothing enforced them matching). Living here, in {@code domain}, and not in {@code
     * GfxConfig}, respects the one-way dependency direction the rest of the codebase already
     * requires: {@code graphics} may depend on {@code domain}, never the other way around, so the
     * source of truth for a domain concept (the size of the world) has to live on this side of
     * that line for {@code GfxConfig} to be allowed to read it at all.
     */
    public static final int STANDARD_WIDTH = 256;

    public static final int STANDARD_HEIGHT = 256;

    /**
     * Owner's known gap from X-04 (DEV_TASKS.md), left alone deliberately: these positions were
     * chosen for the OLD 96x64 map and still sit inside that same corner of the new, much bigger
     * {@value #STANDARD_WIDTH}x{@value #STANDARD_HEIGHT} one — most of the enlarged map is
     * currently ore-free. Rescaling them was considered and rejected for this task: a wide range
     * of tests ({@code WorldTest}, {@code MinerTest}, {@code PatchOreLayoutTest},
     * {@code JsonSaveRepositoryTest}, the headless {@code Main}) all hard-code {@code (6, 5)} as
     * "the center of the standard map's first iron patch" AND build small worlds (some as small as
     * 4x4-12x8) that the patch has to still fall inside — moving it (or any patch) would break
     * every one of them at once for a scope this card doesn't ask for. Spreading ore across the
     * full new map is a real follow-up, just not one this task takes on silently.
     */
    private static final OrePatch[] PATCHES = {
            new OrePatch(6, 5, 3, VanillaItems.IRON_ORE), new OrePatch(9, 14, 3, VanillaItems.IRON_ORE),
            new OrePatch(25, 6, 4, VanillaItems.IRON_ORE), new OrePatch(28, 15, 3, VanillaItems.IRON_ORE),
            new OrePatch(52, 10, 4, VanillaItems.IRON_ORE), new OrePatch(74, 20, 3, VanillaItems.IRON_ORE),
            new OrePatch(45, 34, 4, VanillaItems.IRON_ORE), new OrePatch(14, 44, 3, VanillaItems.IRON_ORE),
            new OrePatch(60, 52, 4, VanillaItems.COPPER_ORE), new OrePatch(84, 42, 3, VanillaItems.COPPER_ORE),
            new OrePatch(33, 56, 3, VanillaItems.COPPER_ORE), new OrePatch(88, 8, 3, VanillaItems.COPPER_ORE),
            // Coal (D-05, DEV_TASKS.md) — smaller deposits than iron/copper (radius 2, not 3-4),
            // one placed deliberately near EACH existing iron/copper cluster (checked by hand for
            // no overlap): a furnace needs both an ore belt AND a coal belt converging on it now
            // (§2.3 of the design audit), so coal being reachable near ore, not off on its own,
            // is what keeps that a solvable planning puzzle instead of a scavenger hunt.
            new OrePatch(2, 10, 2, VanillaItems.COAL), new OrePatch(20, 10, 2, VanillaItems.COAL),
            new OrePatch(65, 15, 2, VanillaItems.COAL), new OrePatch(70, 48, 2, VanillaItems.COAL),
            // Этап 0 — новое сырьё для электронной цепочки, свежий участок карты (x>=100), чтобы не
// пересекаться ни с одной существующей жилой и ни с одним TERRAIN_PATCHES (все y>=53 —
// здесь всё в пределах y<=45, конфликта нет).
            new OrePatch(110, 10, 4, VanillaItems.QUARTZ_SAND), new OrePatch(140, 30, 3, VanillaItems.QUARTZ_SAND),
            new OrePatch(175, 15, 4, VanillaItems.QUARTZ_SAND), new OrePatch(205, 35, 3, VanillaItems.QUARTZ_SAND),
            new OrePatch(185, 8, 3, VanillaItems.TIN_ORE), new OrePatch(220, 25, 3, VanillaItems.TIN_ORE),
// Свинец — рядом с оловом (как уголь рядом с железом, D-05): паяльная линия требует, чтобы
// обе ленты — с олова и со свинца — сходились в одном месте, а не были раскиданы по карте.
            new OrePatch(190, 12, 2, VanillaItems.LEAD_ORE), new OrePatch(225, 30, 2, VanillaItems.LEAD_ORE),
            new OrePatch(240, 10, 3, VanillaItems.CRUDE_OIL), new OrePatch(245, 45, 3, VanillaItems.CRUDE_OIL),
// Золото — редкое (пометка документа): маленький радиус, всего два месторождения на всю карту.
            new OrePatch(248, 5, 1, VanillaItems.GOLD_ORE), new OrePatch(100, 48, 1, VanillaItems.GOLD_ORE),
    };

    /**
     * Obstacles (X-02, DEV_TASKS.md), placed well clear of every {@link #PATCHES} entry above
     * (all of which sit within roughly x&lt;92, y&lt;60 — the old 96x64 map's corner, see that
     * field's javadoc) so there's no need to resolve an overlap: these coordinates simply don't
     * reach that region at all. {@link #terrainAt} still lets ore win any accidental overlap
     * (checked first, in the constructor below) as a second, belt-and-suspenders guarantee.
     */
    private static final TerrainPatch[] TERRAIN_PATCHES = {
            new TerrainPatch(130, 90, 8, Terrain.WATER), new TerrainPatch(200, 60, 7, Terrain.WATER),
            new TerrainPatch(110, 190, 9, Terrain.WATER),
            new TerrainPatch(210, 150, 7, Terrain.ROCK), new TerrainPatch(150, 220, 8, Terrain.ROCK),
            new TerrainPatch(230, 220, 6, Terrain.ROCK),
    };

    private final int width;
    private final int height;
    private final @Nullable ItemType[] grid;
    private final Terrain[] terrainGrid;
    /**
     * Calls to {@link #extract} per cell so far, parallel to {@link #grid} — see {@link
     * OreDepletion} (D-04, DEV_TASKS.md). Zeroed at construction: every fresh {@code
     * PatchOreLayout} starts fully unspoiled, which is exactly why {@link #standard} builds a new
     * one on every call rather than caching one — see that method's javadoc.
     */
    private final int[] extractedCount;

    private PatchOreLayout(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new ItemType[width * height];
        this.extractedCount = new int[width * height];
        this.terrainGrid = new Terrain[width * height];
        Arrays.fill(terrainGrid, Terrain.GROUND);

        // Bounding-box rasterization, not a full width×height scan checking every patch per cell
        // (code review finding): PATCHES.length patches, each touching only its own small circle
        // of cells, instead of width×height×PATCHES.length "does this patch contain this cell"
        // checks. Ore first, all of it, before any terrain — the grid[index] == null guard inside
        // rasterizeOre preserves "first patch in declaration order wins an overlap," the same
        // priority the original cell-major scan gave for free; terrain then only paints cells ore
        // left empty, preserving "ore always wins" exactly.
        for (OrePatch patch : PATCHES) {
            rasterizeOre(patch, width, height);
        }
        for (TerrainPatch patch : TERRAIN_PATCHES) {
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
                    if (grid[index] == null) { // first patch in declaration order wins an overlap
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
     * The game's built-in map — twenty-eight patches: eight iron, four copper, four coal, four
     * sand, two tin, two lead, two oil, two gold (D-05, Этап 0 PC-roadmap). Builds
     * a brand-new instance every call, deliberately NOT a cached singleton (D-04, DEV_TASKS.md):
     * once ore depletion made this class stateful and mutable, every caller sharing one cached
     * instance would have shared its depletion too — a test exhausting a cell would leave it thin
     * for the next unrelated {@code World} built in the same JVM. Rebuilding touches each patch's
     * own small bounding box, not the whole {@code width}×{@code height} grid (code review
     * finding) — already microseconds before that change, paid once per {@code World}
     * construction, never per tick; still worth doing since it was free to do correctly.
     */
    public static PatchOreLayout standard() {
        return new PatchOreLayout(STANDARD_WIDTH, STANDARD_HEIGHT);
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

    /**
     * Always the same fixed map, so {@code seed} carries no meaning here — but the dimensions do
     * (N11, NEW_BUGS_PROGRESS.md). They used to be reported as {@code 0, 0} while this class was
     * building a {@value #STANDARD_WIDTH}x{@value #STANDARD_HEIGHT} grid; this identifier is the one
     * thing {@code JsonSaveRepository.load} compares before accepting a save (P2-01, owner decision
     * A), so a component of it stating something untrue about the map is exactly the wrong place for
     * a placeholder.
     */
    @Override
    public OreLayoutId id() {
        return new OreLayoutId("patch", 0, width, height);
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
    public Terrain terrainAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Terrain.ROCK; // fail closed — off the generated grid entirely, never buildable
        }
        return terrainGrid[y * width + x];
    }
}
