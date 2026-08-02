package com.graphics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MenuLayout} — pure geometry (no libGDX types), shared by {@link MenuRenderer} (drawing)
 * and {@code com.graphics.screen.MainMenuScreen} (hit-testing mouse hover/click), same split
 * {@link BuildMenuLayoutTest} already covers for the build menu's own grid.
 *
 * <p>Screen 1280x800 throughout ({@code GfxConfig.WINDOW_W}/{@code WINDOW_H}) with a 3-item list —
 * the exact pixel bands below were derived from {@link MenuLayout}'s own constants (panel width
 * 560, padding 24, title 34, row height 30), not guessed, so a constant tweak that silently breaks
 * hit-testing fails one of these instead of only showing up as an unclickable menu row in play.
 */
class MenuLayoutTest {

    private static final int SCREEN_W = 1280;
    private static final int SCREEN_H = 800;

    @Test
    void hitsRowZeroNearTheTopOfTheList() {
        assertEquals(0, MenuLayout.hitTestRow(410, 400, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void hitsRowOneOneRowLowerThanRowZero() {
        assertEquals(1, MenuLayout.hitTestRow(410, 420, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void hitsTheLastRowOfAThreeItemList() {
        assertEquals(2, MenuLayout.hitTestRow(410, 450, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void clickingBelowTheLastRowMissesEvenThoughItsStillNearThePanel() {
        assertEquals(-1, MenuLayout.hitTestRow(410, 480, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void clickingAboveTheTitleMisses() {
        assertEquals(-1, MenuLayout.hitTestRow(410, 100, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void clickingLeftOfThePanelMissesRegardlessOfHeight() {
        assertEquals(-1, MenuLayout.hitTestRow(100, 400, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void clickingRightOfThePanelMissesRegardlessOfHeight() {
        assertEquals(-1, MenuLayout.hitTestRow(1000, 400, SCREEN_W, SCREEN_H, 3, false));
    }

    @Test
    void anEmptyListNeverHitsAnything() {
        assertEquals(-1, MenuLayout.hitTestRow(410, 400, SCREEN_W, SCREEN_H, 0, false));
    }

    @Test
    void aStatusLineGrowsThePanelAndShiftsWhichRowTheSamePixelHits() {
        // The panel grows (and stays screen-centered) once a status line is shown, pushing every
        // row's band higher on screen — the exact same (x, y) that hits row 0 without a status
        // line falls into row 1's band once one is showing.
        assertEquals(0, MenuLayout.hitTestRow(410, 400, SCREEN_W, SCREEN_H, 3, false));
        assertEquals(1, MenuLayout.hitTestRow(410, 400, SCREEN_W, SCREEN_H, 3, true));
    }
}
