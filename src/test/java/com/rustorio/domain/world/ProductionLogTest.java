package com.rustorio.domain.world;

import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ProductionLog}: the last {@code CAPACITY} produced items, most recent first — a live
 * invariant that had no test at all before this (A3, CODE_REVIEW_2026-07-28.md).
 */
class ProductionLogTest {

    @Test
    void mostRecentComesFirst() {
        ProductionLog log = new ProductionLog();
        log.onProduced(1, VanillaItems.IRON_ORE);
        log.onProduced(2, VanillaItems.GEAR);

        assertEquals(List.of(VanillaItems.GEAR, VanillaItems.IRON_ORE), log.recent());
    }

    @Test
    void ringDropsTheOldestPastCapacity() {
        ProductionLog log = new ProductionLog();
        ItemType[] items = {VanillaItems.IRON_ORE, VanillaItems.COAL, VanillaItems.GEAR, VanillaItems.BRONZE_ORE, VanillaItems.ENGINE, VanillaItems.CHASSIS};
        for (int i = 0; i < items.length; i++) {
            log.onProduced(i, items[i]); // six events, capacity five — IRON_ORE (the first) must fall off
        }

        assertEquals(List.of(VanillaItems.CHASSIS, VanillaItems.ENGINE, VanillaItems.BRONZE_ORE, VanillaItems.GEAR, VanillaItems.COAL), log.recent());
    }

    @Test
    void emptyLogReturnsAnEmptyList() {
        assertEquals(List.of(), new ProductionLog().recent());
    }
}
