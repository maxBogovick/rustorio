package com.graphics.render;

import com.rustorio.domain.BuildingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HotbarLayout} — one formula shared by both the hotbar's drawing ({@code HudRenderer}) and
 * its hit-testing ({@code com.graphics.input.InputHandler}), pure arithmetic, no libGDX types (A1,
 * CODE_REVIEW_2026-07-28.md).
 */
class HotbarLayoutTest {

    private static final int SCREEN_W = 1280;
    private static final int SCREEN_H = 800;

    @Test
    void countMatchesTheNumberOfBuildingTypes() {
        assertEquals(BuildingType.values().length, HotbarLayout.count());
    }

    @Test
    void hitTestFindsTheSlotUnderThePoint() {
        float x = HotbarLayout.slotX(2, SCREEN_W) + HotbarLayout.SLOT_SIZE / 2f;
        float screenY = SCREEN_H - (HotbarLayout.slotY() + HotbarLayout.SLOT_SIZE / 2f); // hitTest flips Y itself

        assertEquals(2, HotbarLayout.hitTest(x, screenY, SCREEN_W, SCREEN_H));
    }

    @Test
    void hitTestMissesAboveTheSlotRow() {
        float x = HotbarLayout.slotX(0, SCREEN_W) + 1;

        assertEquals(-1, HotbarLayout.hitTest(x, 0, SCREEN_W, SCREEN_H), "the very top of the window is nowhere near the hotbar");
    }

    @Test
    void hitTestMissesInTheGapBetweenTwoSlots() {
        float gapX = HotbarLayout.slotX(0, SCREEN_W) + HotbarLayout.SLOT_SIZE + HotbarLayout.SLOT_GAP / 2f;
        float screenY = SCREEN_H - (HotbarLayout.slotY() + HotbarLayout.SLOT_SIZE / 2f);

        assertEquals(-1, HotbarLayout.hitTest(gapX, screenY, SCREEN_W, SCREEN_H));
    }

    @Test
    void slotXCentersThePanelOnScreenWidth() {
        float firstSlotX = HotbarLayout.slotX(0, SCREEN_W);
        float lastSlotX = HotbarLayout.slotX(HotbarLayout.count() - 1, SCREEN_W);

        float leftMargin = firstSlotX;
        float rightMargin = SCREEN_W - (lastSlotX + HotbarLayout.SLOT_SIZE);
        assertEquals(leftMargin, rightMargin, 0.01f, "the panel must be centered, not left/right-biased");
    }
}
