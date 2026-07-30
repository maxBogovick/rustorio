package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Palette}'s per-{@link ItemType}/{@link BuildingStatus} lookups — every kind must resolve to
 * SOMETHING, so a newly added item can't silently fall through to a leftover default the way
 * {@code WorldRenderer.oreColor} does for ore (A1, CODE_REVIEW_2026-07-28.md). {@code
 * itemColor}/{@code itemShape} read straight off {@link ItemType}'s own fields now, so there's
 * no {@code switch} left to be exhaustive — this pins the RUNTIME
 * behavior down instead, and documents the "empty exactly for WORKING" contract {@code
 * BuildingRenderer} relies on to draw no marker at all for a healthy building.
 */
class PaletteTest {

    @Test
    void everyItemHasAColorAndAShape() {
        for (ItemType item : VanillaItems.frozen().iterate()) {
            assertNotNull(Palette.itemColor(item), item + " has no color");
            assertNotNull(Palette.itemShape(item), item + " has no shape");
        }
    }

    /**
     * Regression test for code review finding S5: {@code itemColor} used to allocate a fresh
     * {@link Color} on every single call — {@code ItemRenderer} calls it per visible cargo item
     * EVERY FRAME, which is exactly the "allocate on a hot path" pattern {@code AGENTS.md} bans.
     */
    @Test
    void itemColorReturnsTheSameCachedInstanceForTheSameRgb() {
        Color first = Palette.itemColor(VanillaItems.IRON_ORE);
        Color second = Palette.itemColor(VanillaItems.IRON_ORE);

        assertSame(first, second, "must be a cached instance, not a fresh allocation per call");
    }

    /**
     * The cache key is the raw packed int, not {@link ItemType} identity — two DIFFERENT items
     * (different ids) that happen to share the same {@code colorRgb} must still get the very same
     * {@link Color} object (there is nothing per-item to distinguish, only per-color).
     */
    @Test
    void itemColorCachesByTheRawColorNotByItemIdentity() {
        ItemType a = new ItemType(ContentId.of("test:a"), "A", false, 0x123456, ItemShape.CIRCLE);
        ItemType b = new ItemType(ContentId.of("test:b"), "B", false, 0x123456, ItemShape.SQUARE);

        assertSame(Palette.itemColor(a), Palette.itemColor(b));
    }

    @Test
    void itemColorDecodesTheRgbComponentsCorrectly() {
        ItemType item = new ItemType(ContentId.of("test:swatch"), "Swatch", false, 0x11_22_33, ItemShape.CIRCLE);

        Color color = Palette.itemColor(item);

        assertEquals(0x11 / 255f, color.r, 0.001f);
        assertEquals(0x22 / 255f, color.g, 0.001f);
        assertEquals(0x33 / 255f, color.b, 0.001f);
    }

    @Test
    void statusColorIsEmptyOnlyForWorking() {
        assertTrue(Palette.statusColor(BuildingStatus.WORKING).isEmpty(),
                "BuildingRenderer relies on this to draw no marker at all for a healthy building");
        for (BuildingStatus status : BuildingStatus.values()) {
            if (status != BuildingStatus.WORKING) {
                assertFalse(Palette.statusColor(status).isEmpty(), status + " must have a marker color");
            }
        }
    }
}
