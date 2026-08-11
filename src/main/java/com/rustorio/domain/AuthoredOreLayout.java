package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.model.AuthoredMap;
import com.rustorio.api.content.model.OrePatch;
import com.rustorio.api.content.model.TerrainPatch;
import com.rustorio.api.content.vanilla.VanillaItems;

/**
 * A third {@link OreLayout} implementation, alongside {@link PatchOreLayout} (fixed vanilla map)
 * and {@link RandomOreLayout} (seeded): a mod author's own {@link AuthoredMap}, drawn through the
 * content editor's canvas instead of hardcoded in Java or rolled from a seed. Same fixed-size
 * ({@value PatchOreLayout#STANDARD_WIDTH}x{@value PatchOreLayout#STANDARD_HEIGHT}), same
 * precompute-once-into-a-flat-array construction, same "ore always wins, first patch in list order
 * wins an overlap" rule as the other two — see their own constructors' javadoc for why; kept as a
 * third copy of that same handful of lines rather than a shared helper (owner's existing, accepted
 * trade-off across the first two implementations already, not something this class's own task
 * reopens).
 */
public final class AuthoredOreLayout implements OreLayout {

    private final ContentId mapId;
    private final int width;
    private final int height;
    private final @Nullable ItemType[] grid;
    /** Null means plain ground — see {@link OreLayout#terrainAt}. */
    private final @Nullable ItemType[] terrainGrid;
    /** Calls to {@link #extract} per cell so far, parallel to {@link #grid} — see {@link OreDepletion}. */
    private final int[] extractedCount;

    private AuthoredOreLayout(AuthoredMap map) {
        this.mapId = map.id();
        this.width = PatchOreLayout.STANDARD_WIDTH;
        this.height = PatchOreLayout.STANDARD_HEIGHT;
        this.grid = new ItemType[width * height];
        this.extractedCount = new int[width * height];
        this.terrainGrid = new ItemType[width * height]; // all null = all plain ground, no fill needed

        for (OrePatch patch : map.orePatches()) {
            rasterizeOre(patch);
        }
        for (TerrainPatch patch : map.terrainPatches()) {
            rasterizeTerrain(patch);
        }
    }

    /** A fresh instance from {@code map} — a new instance every call, never cached, exactly like {@link PatchOreLayout#standard()} (see its own javadoc for why: depletion state must never leak between unrelated worlds sharing one map definition). */
    public static AuthoredOreLayout from(AuthoredMap map) {
        return new AuthoredOreLayout(map);
    }

    private void rasterizeOre(OrePatch patch) {
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

    private void rasterizeTerrain(TerrainPatch patch) {
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

    /** {@code "authored:" + the map's own ContentId} — distinct per authored map, so a save built on one never silently loads against another (see {@link OreLayoutId}'s own javadoc). */
    @Override
    public OreLayoutId id() {
        return new OreLayoutId("authored:" + mapId, 0, width, height);
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
