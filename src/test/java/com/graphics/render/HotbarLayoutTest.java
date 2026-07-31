package com.graphics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HotbarLayout} — one formula shared by both the hotbar's drawing ({@code HudRenderer}) and
 * its hit-testing ({@code com.graphics.input.InputHandler}), pure arithmetic, no libGDX types (A1,
 * CODE_REVIEW_2026-07-28.md). {@code slotCount} is a parameter (Phase 8 — a configurable hotbar,
 * not a fixed {@code BuildingType.values().length}), so every test below picks its own count
 * instead of reading it off the closed vanilla set.
 */
class HotbarLayoutTest {

    private static final int SCREEN_W = 1280;
    private static final int SCREEN_H = 800;
    private static final int SLOTS = 12;

    @Test
    void hitTestFindsTheSlotUnderThePoint() {
        float x = HotbarLayout.slotX(2, SCREEN_W, SLOTS) + HotbarLayout.SLOT_SIZE / 2f;
        float screenY = SCREEN_H - (HotbarLayout.slotY() + HotbarLayout.SLOT_SIZE / 2f); // hitTest flips Y itself

        assertEquals(2, HotbarLayout.hitTest(x, screenY, SCREEN_W, SCREEN_H, SLOTS));
    }

    @Test
    void hitTestMissesAboveTheSlotRow() {
        float x = HotbarLayout.slotX(0, SCREEN_W, SLOTS) + 1;

        assertEquals(-1, HotbarLayout.hitTest(x, 0, SCREEN_W, SCREEN_H, SLOTS), "the very top of the window is nowhere near the hotbar");
    }

    @Test
    void hitTestMissesInTheGapBetweenTwoSlots() {
        float gapX = HotbarLayout.slotX(0, SCREEN_W, SLOTS) + HotbarLayout.SLOT_SIZE + HotbarLayout.SLOT_GAP / 2f;
        float screenY = SCREEN_H - (HotbarLayout.slotY() + HotbarLayout.SLOT_SIZE / 2f);

        assertEquals(-1, HotbarLayout.hitTest(gapX, screenY, SCREEN_W, SCREEN_H, SLOTS));
    }

    @Test
    void slotXCentersThePanelOnScreenWidth() {
        float firstSlotX = HotbarLayout.slotX(0, SCREEN_W, SLOTS);
        float lastSlotX = HotbarLayout.slotX(SLOTS - 1, SCREEN_W, SLOTS);

        float leftMargin = firstSlotX;
        float rightMargin = SCREEN_W - (lastSlotX + HotbarLayout.SLOT_SIZE);
        assertEquals(leftMargin, rightMargin, 0.01f, "the panel must be centered, not left/right-biased");
    }

    @Test
    void differentSlotCountsProduceDifferentlyWidePanels() {
        float narrow = HotbarLayout.totalWidth(3);
        float wide = HotbarLayout.totalWidth(12);

        assertEquals(3 * HotbarLayout.SLOT_SIZE + 2 * HotbarLayout.SLOT_GAP, narrow);
        assertEquals(12 * HotbarLayout.SLOT_SIZE + 11 * HotbarLayout.SLOT_GAP, wide);
    }

    @Test
    void hitTestRespectsTheGivenSlotCountNotAFixedOne() {
        // A point past the 3rd slot of a 3-slot hotbar must miss even though it would land inside
        // slot 3 of a wider, e.g. 12-slot, hotbar.
        float x = HotbarLayout.slotX(3, SCREEN_W, 12) + 1;
        float screenY = SCREEN_H - (HotbarLayout.slotY() + HotbarLayout.SLOT_SIZE / 2f);

        assertEquals(-1, HotbarLayout.hitTest(x, screenY, SCREEN_W, SCREEN_H, 3));
    }
}
