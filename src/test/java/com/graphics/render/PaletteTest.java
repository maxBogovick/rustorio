package com.graphics.render;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Palette}'s per-{@link Item}/{@link BuildingStatus} lookups — every kind must resolve to
 * SOMETHING, so a newly added item can't silently fall through to a leftover default the way
 * {@code WorldRenderer.oreColor} does for ore (A1, CODE_REVIEW_2026-07-28.md). {@code
 * itemColor}/{@code itemShape} are already exhaustive {@code switch}es with no {@code default}
 * branch — the compiler already refuses a build that forgets a case — this pins the RUNTIME
 * behavior down too, and documents the "empty exactly for WORKING" contract {@code
 * BuildingRenderer} relies on to draw no marker at all for a healthy building.
 */
class PaletteTest {

    @Test
    void everyItemHasAColorAndAShape() {
        for (Item item : Item.values()) {
            assertNotNull(Palette.itemColor(item), item + " has no color");
            assertNotNull(Palette.itemShape(item), item + " has no shape");
        }
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
