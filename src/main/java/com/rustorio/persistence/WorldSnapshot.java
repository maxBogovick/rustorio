package com.rustorio.persistence;

import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.Research;
import com.rustorio.domain.world.ProductionStats;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The entire persisted state of a {@code World}: production totals, research, every building, and
 * which ore map it was all built on.
 *
 * <p><b>Owner decision (P2-01, BUG_FIX_PROGRESS.md):</b> option (A) — a save whose {@link
 * #oreLayout} doesn't match the world currently being loaded into fails outright ({@code
 * JsonSaveRepository.load} returns {@code SaveResult.Failure}) rather than silently placing
 * miners on ore-less ground. {@code oreLayout} is {@code @Nullable} so saves written before this
 * field existed still load: {@code null} means "unknown map, don't check."
 */
record WorldSnapshot(
        ProductionStats.Snapshot stats,
        Research.Snapshot research,
        List<PlacedBuilding> buildings,
        @Nullable OreLayoutId oreLayout) {
}
