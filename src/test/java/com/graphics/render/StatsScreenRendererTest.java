package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link StatsScreenRenderer#visibleItems} - pure list-slicing, no libGDX, same reason {@link
 * BuildMenuLayoutTest} exercises {@code BuildMenuLayout.visibleTiles} directly.
 *
 * <p>Live bug report: items are JSON content loaded through the same open registry as buildings
 * ({@code com.rustorio.mod}) - the Phase 8 acceptance test alone stress-tests 200 of them. Before
 * this class had its own cap, that many items drew a stats panel far taller than the default
 * window with the bottom rows simply running off-screen, no scrollbar, no indication anything was
 * missing - the exact class of problem {@code BuildMenuLayout.MAX_VISIBLE_TILES} was already
 * hardened against for buildings.
 */
class StatsScreenRendererTest {

    @Test
    void aShortListIsReturnedAsIs() {
        List<ItemType> few = List.of(VanillaItems.IRON_ORE, VanillaItems.IRON_PLATE);

        assertSame(few, StatsScreenRenderer.visibleItems(few), "no truncation needed, returns the same list");
    }

    @Test
    void aListLongerThanTheCapIsTruncatedNotHidden() {
        List<ItemType> tooMany = java.util.stream.IntStream.range(0, StatsScreenRenderer.MAX_VISIBLE_ITEMS + 200)
                .mapToObj(StatsScreenRendererTest::itemNamed)
                .toList();

        List<ItemType> visible = StatsScreenRenderer.visibleItems(tooMany);

        assertEquals(StatsScreenRenderer.MAX_VISIBLE_ITEMS, visible.size());
        assertEquals(tooMany.subList(0, StatsScreenRenderer.MAX_VISIBLE_ITEMS), visible,
                "truncates from the front, doesn't drop/reorder arbitrarily");
    }

    @Test
    void exactlyAtTheCapIsNotTruncated() {
        List<ItemType> exact = java.util.stream.IntStream.range(0, StatsScreenRenderer.MAX_VISIBLE_ITEMS)
                .mapToObj(StatsScreenRendererTest::itemNamed)
                .toList();

        assertEquals(StatsScreenRenderer.MAX_VISIBLE_ITEMS, StatsScreenRenderer.visibleItems(exact).size());
    }

    private static ItemType itemNamed(int i) {
        return new ItemType(ContentId.of("stress:item_" + i), "Item " + i, false, 0x808080, ItemShape.CIRCLE);
    }
}
