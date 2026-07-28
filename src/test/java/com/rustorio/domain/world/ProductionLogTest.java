package com.rustorio.domain.world;

import com.rustorio.domain.Item;
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
        log.onProduced(1, Item.IRON_ORE);
        log.onProduced(2, Item.GEAR);

        assertEquals(List.of(Item.GEAR, Item.IRON_ORE), log.recent());
    }

    @Test
    void ringDropsTheOldestPastCapacity() {
        ProductionLog log = new ProductionLog();
        Item[] items = {Item.IRON_ORE, Item.COAL, Item.GEAR, Item.BRONZE_ORE, Item.ENGINE, Item.CHASSIS};
        for (int i = 0; i < items.length; i++) {
            log.onProduced(i, items[i]); // six events, capacity five — IRON_ORE (the first) must fall off
        }

        assertEquals(List.of(Item.CHASSIS, Item.ENGINE, Item.BRONZE_ORE, Item.GEAR, Item.COAL), log.recent());
    }

    @Test
    void emptyLogReturnsAnEmptyList() {
        assertEquals(List.of(), new ProductionLog().recent());
    }
}
