package com.rustorio.persistence;

import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.Research;
import com.rustorio.domain.world.PlayerInventory;
import com.rustorio.domain.world.ProductionStats;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The entire persisted state of a {@code World}: production totals, research, every building,
 * which ore map it was all built on, and (D-07, DEV_TASKS.md) the player's inventory and every
 * ore cell's depletion — the two pieces of Phase 2 state that weren't in any snapshot until now.
 *
 * <p><b>Owner decision (P2-01, BUG_FIX_PROGRESS.md):</b> option (A) — a save whose {@link
 * #oreLayout} doesn't match the world currently being loaded into fails outright ({@code
 * JsonSaveRepository.load} returns {@code SaveResult.Failure}) rather than silently placing
 * miners on ore-less ground. {@code oreLayout} is {@code @Nullable} so saves written before this
 * field existed still load: {@code null} means "unknown map, don't check."
 *
 * <p><b>Owner decision (D-07, DEV_TASKS.md):</b> {@link #version} follows the same "field didn't
 * exist yet" reasoning one step further — instead of every NEW field needing its own {@code
 * @Nullable} migration path forever, {@code JsonSaveRepository.load} now rejects anything whose
 * {@code version} isn't {@link #CURRENT_VERSION} outright. A save written before this field
 * existed deserializes {@code version} as {@code 0} (a primitive {@code int} can't be {@code
 * null}, so Jackson defaults a missing one), which is never equal to {@code CURRENT_VERSION} —
 * old saves get the same clean rejection as a save from a hypothetical future format, with no
 * extra code. {@link #inventory}/{@link #oreDepletion} can therefore be plain, non-nullable
 * fields: any snapshot that passes the version check was necessarily written by code that also
 * wrote these two, so there's no partial/old-format case left to degrade gracefully for.
 *
 * <p><b>Owner decision (F-03, DEV_TASKS.md):</b> {@link #CURRENT_VERSION} bumped again to add
 * {@code FurnaceState.selectedRecipeOutput} — the field itself is {@code @Nullable} (a save from
 * before this field existed would deserialize it as {@code null} just fine on its own), but this
 * class's own {@code version} check rejects anything below {@link #CURRENT_VERSION} wholesale
 * regardless, so an un-bumped version here would never actually let that graceful nullable
 * fallback run. Bumping is the documented policy for ANY save format change, not just ones a
 * missing-field default can't handle by itself.
 *
 * <p><b>Owner decision (X-01, DEV_TASKS.md):</b> bumped a third time — {@code SplitterState}
 * replaced its {@code @Nullable String rule} field with a non-nullable {@code boolean
 * nextIsForward}, an incompatible shape change (not just a new optional field), plus two brand new
 * memento kinds ({@code FilterState}, {@code InserterState}) a pre-X-01 save can't have. A save
 * from before this change would deserialize a missing {@code nextIsForward} as {@code false}
 * (Jackson's primitive default) silently — wrong, not a clean failure — which is exactly the
 * "reject the whole snapshot" case this version check exists for.
 *
 * <p><b>N3 (NEW_BUGS_PROGRESS.md):</b> bumped a fourth time for {@link #tickCount}, the world's own
 * clock. It was the last piece of {@code World} state left out of here: {@link #stats} entries are
 * timestamped in ticks and were already persisted, so restoring them against a clock reset to zero
 * put the two permanently out of step. Same reasoning as the bumps above — a pre-N3 save would
 * deserialize a missing {@code tickCount} as {@code 0} (Jackson's primitive default), which is
 * exactly the silently-wrong value this field exists to stop.
 *
 * <p><b>Owner decision:</b> bumped a fifth time — the item enum backing {@link #inventory}'s keys
 * and {@code ChestState}'s contents became a registry-backed prototype, identified by a namespaced
 * string (e.g. {@code "rustorio:iron_ore"}) instead of a bare enum name (e.g. {@code "IRON_ORE"}).
 * A pre-bump save's keys wouldn't parse as the new identifier format at all — not a
 * gracefully-defaultable missing field, an outright unreadable one — so this is exactly the
 * "reject the whole snapshot" case the version check exists for, not a silent break.
 */
record WorldSnapshot(
        int version,
        ProductionStats.Snapshot stats,
        Research.Snapshot research,
        List<PlacedBuilding> buildings,
        @Nullable OreLayoutId oreLayout,
        Map<ItemType, Integer> inventory,
        Map<Integer, Integer> oreDepletion,
        long tickCount) {

    /** Bump this whenever the save format changes — see the class javadoc's bump history. */
    static final int CURRENT_VERSION = 5;
}
