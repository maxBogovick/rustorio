package com.rustorio.domain.world;

import com.rustorio.api.content.model.ItemType;
import java.util.List;

/**
 * Read-only face of {@link ProductionLog} — the same discipline {@link ProductionStatsView} and
 * {@link com.rustorio.domain.ResearchView} already follow, extended here for consistency (P3-08,
 * BUG_FIX_PROGRESS.md): the rendering layer only ever needs to read the recent-items list, never
 * to feed it new events itself ({@link ProductionListener#onProduced} stays off this interface).
 */
public interface ProductionLogView {

    List<ItemType> recent();
}
