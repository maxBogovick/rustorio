package com.graphics.input;

import com.graphics.input.SimulationControls.OverlayPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimulationControls}'s panel state machine — {@link SimulationControls#toggleOrSwitch}
 * and {@link SimulationControls#closeAnyOpenPanel} are pure (no libGDX), unlike {@link
 * SimulationControls#handle()} itself, which reads {@code Gdx.input} directly and so can't be
 * driven headlessly (same constraint {@code CameraViewport}'s own class javadoc explains).
 *
 * <p>Pins down two live bug reports fixed together: (1) pressing a second panel's key while one
 * was already open used to leave BOTH open, drawn on top of each other at the same screen
 * position, instead of switching between them; (2) ESC used to do nothing for any of the four
 * modal panels (only the separate inspection panel in {@code InputHandler}), so the only way out
 * of one was its own specific toggle key.
 */
class SimulationControlsTest {

    @Test
    void nothingIsOpenInitially() {
        SimulationControls controls = new SimulationControls();

        assertFalse(controls.showRecipeBook());
        assertFalse(controls.showTechTree());
        assertFalse(controls.showStats());
        assertFalse(controls.showBuildMenu());
    }

    @Test
    void toggleOrSwitchOpensThenClosesTheSamePanel() {
        SimulationControls controls = new SimulationControls();

        controls.toggleOrSwitch(OverlayPanel.RECIPE_BOOK);
        assertTrue(controls.showRecipeBook());

        controls.toggleOrSwitch(OverlayPanel.RECIPE_BOOK);
        assertFalse(controls.showRecipeBook(), "pressing the same panel's key again closes it");
    }

    @Test
    void openingADifferentPanelSwitchesInsteadOfStackingOnTop() {
        SimulationControls controls = new SimulationControls();
        controls.toggleOrSwitch(OverlayPanel.RECIPE_BOOK);

        controls.toggleOrSwitch(OverlayPanel.TECH_TREE);

        assertTrue(controls.showTechTree());
        assertFalse(controls.showRecipeBook(),
                "live bug report: TAB then T used to leave both the recipe book AND the tech tree "
                        + "open at once, drawn on top of each other at the same screen position");
    }

    @Test
    void everyPanelIsMutuallyExclusiveWithEveryOther() {
        OverlayPanel[] panels = {OverlayPanel.RECIPE_BOOK, OverlayPanel.TECH_TREE, OverlayPanel.STATS, OverlayPanel.BUILD_MENU};
        for (OverlayPanel first : panels) {
            for (OverlayPanel second : panels) {
                if (first == second) {
                    continue;
                }
                SimulationControls controls = new SimulationControls();
                controls.toggleOrSwitch(first);
                controls.toggleOrSwitch(second);
                assertTrue(isOpen(controls, second), second + " should be open");
                assertFalse(isOpen(controls, first), first + " should have been switched away from, not left open alongside " + second);
            }
        }
    }

    @Test
    void closeAnyOpenPanelClosesWhicheverOneIsOpen() {
        SimulationControls controls = new SimulationControls();
        controls.toggleOrSwitch(OverlayPanel.BUILD_MENU);

        controls.closeAnyOpenPanel();

        assertFalse(controls.showBuildMenu(),
                "live bug report: ESC used to do nothing at all for the build menu (or the recipe "
                        + "book / tech tree / stats screen) — only its own toggle key closed it");
    }

    @Test
    void closeAnyOpenPanelIsANoOpWhenNothingIsOpen() {
        SimulationControls controls = new SimulationControls();

        controls.closeAnyOpenPanel(); // must not throw, must not "open" anything

        assertFalse(controls.showRecipeBook());
        assertFalse(controls.showTechTree());
        assertFalse(controls.showStats());
        assertFalse(controls.showBuildMenu());
    }

    private static boolean isOpen(SimulationControls controls, OverlayPanel panel) {
        return switch (panel) {
            case RECIPE_BOOK -> controls.showRecipeBook();
            case TECH_TREE -> controls.showTechTree();
            case STATS -> controls.showStats();
            case BUILD_MENU -> controls.showBuildMenu();
            case NONE -> throw new IllegalArgumentException("NONE isn't a panel to check");
        };
    }
}
