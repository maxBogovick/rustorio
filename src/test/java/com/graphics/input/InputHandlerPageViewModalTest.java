package com.graphics.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure half of page-view / modal click gating. The call site in {@link InputHandler#modalOpen} must
 * pass {@code viewedPage != null} as the second argument — this suite pins the boolean door itself;
 * wiring is reviewed as {@code modalOpen() → swallowsWorldClicks(..., viewedPage != null)}.
 */
class InputHandlerPageViewModalTest {

    @Test
    void aPageViewAloneSwallowsWorldClicks() {
        assertTrue(InputHandler.swallowsWorldClicks(false, true),
                "page view must gate world clicks the same way a full-screen panel does");
    }

    @Test
    void aFullScreenPanelAloneSwallowsWorldClicks() {
        assertTrue(InputHandler.swallowsWorldClicks(true, false));
    }

    @Test
    void neitherPanelNorPageViewLeavesWorldClicksOpen() {
        assertFalse(InputHandler.swallowsWorldClicks(false, false));
    }
}
