package com.rustorio.domain;

import java.util.Map;
import java.util.Optional;
import com.rustorio.api.content.model.ItemType;

/**
 * Strategy pattern: where ore lies on the map and of which kind. {@code World} depends on this
 * interface, not on {@link PatchOreLayout} directly — a test can hand it a fixed, tiny layout
 * instead of the real map, and a future "random seed" world generator is a second implementation,
 * not a change to {@code World} or {@code Miner}.
 *
 * <p><b>Owner decision (X-02, DEV_TASKS.md):</b> {@link #terrainAt} extends this same interface
 * rather than introducing a second, parallel strategy object for terrain — both implementations
 * already generate their whole map in one constructor pass, and keeping ore and terrain on the
 * SAME object is what lets {@link PatchOreLayout}/{@link RandomOreLayout} guarantee terrain never
 * paints over an ore cell (the card's own risk note) by construction, in one place, instead of
 * needing two separately-generated objects reconciled afterward. The name is a known trade-off
 * this leaves behind: "OreLayout" no longer describes only ore. A rename ({@code MapLayout}?
 * {@code TerrainLayout}?) is a reasonable follow-up, just a wider-blast-radius one (every caller
 * of this interface, not just the two implementations) than this task takes on for one field.
 */
public interface OreLayout {

    /** Which ore (if any) lies under cell {@code (x, y)}. Must be deterministic and never mutate state. */
    Optional<ItemType> oreAt(int x, int y);

    default boolean hasOre(int x, int y) {
        return oreAt(x, y).isPresent();
    }

    /**
     * Actually mine one batch from {@code (x, y)}, consuming from that cell's own finite reserve —
     * unlike {@link #oreAt}, which only reports what's there and never changes anything.
     *
     * <p><b>Owner decision (D-04, DEV_TASKS.md):</b> a cell has a large but finite reserve, then an
     * infinite thin tail — it never goes fully empty once it has ore at all (§2.2/В3 of the design
     * audit: finite ore with no tail forces re-planning an old line; infinite ore removes any
     * reason to expand — the tail keeps an old line working, just slower, so relocating becomes an
     * optimization rather than a repair). See {@link OreDepletion} for the shared rule both
     * implementations apply. Returns {@code Optional.empty()} on a call that the current reserve
     * doesn't yield ore for — {@link com.rustorio.domain.building.Miner} already treats an empty
     * result as "try again next cycle," so a depleted cell simply slows a miner down instead of
     * needing any change to its timing logic.
     */
    Optional<ItemType> extract(int x, int y);

    /** Which map this is — see {@link OreLayoutId} for why a save needs to know. */
    OreLayoutId id();

    /**
     * What lies on cell {@code (x, y)} as terrain — see {@link TerrainPatch}. Empty means plain,
     * buildable ground, which is what a cell is when no terrain patch claims it.
     *
     * <p>Empty rather than a {@code GROUND} constant because terrain stopped being a closed enum:
     * with any item namable as terrain, "no obstacle here" is not one of the values a mod could
     * supply, it's the absence of all of them. Must be deterministic and never mutate state.
     */
    Optional<ItemType> terrainAt(int x, int y);

    /** Whether a building may stand on {@code (x, y)} at all, terrain-wise — {@code PlacementRule} is the one caller. */
    default boolean isPassable(int x, int y) {
        return terrainAt(x, y).isEmpty();
    }

    /**
     * Per-cell {@link #extract} call counts, keyed by the flat index {@code y * width + x} each
     * implementation already uses internally — only cells extracted from at least once, everything
     * else defaults to 0 (D-07, DEV_TASKS.md: ore depletion, D-04, wasn't part of any save until
     * now). The key is a plain flat index rather than a dedicated coordinate type because decoding
     * it back to {@code (x, y)} only ever matters to the SAME layout instance that produced it
     * (which already knows its own width) — nothing outside {@link #restoreDepletion} needs to.
     */
    Map<Integer, Integer> depletionSnapshot();

    /**
     * Overwrite depletion state wholesale from a previously captured {@link #depletionSnapshot()}
     * — the inverse operation, used only by {@code JsonSaveRepository.load} after it's already
     * confirmed (via {@link #id()}) that the save's map matches this layout's.
     */
    void restoreDepletion(Map<Integer, Integer> snapshot);
}
