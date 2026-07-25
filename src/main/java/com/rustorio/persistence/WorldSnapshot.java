package com.rustorio.persistence;

import com.rustorio.domain.Research;
import com.rustorio.domain.world.ProductionStats;
import java.util.List;

/** The entire persisted state of a {@code World}: production totals, research, every building. */
record WorldSnapshot(
        ProductionStats.Snapshot stats,
        Research.Snapshot research,
        List<PlacedBuilding> buildings) {
}
