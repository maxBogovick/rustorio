package com.graphics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SettingsModalLayout} — the modal's rows, fitted to the modal's own width. The live report
 * is a web miner's URL field: the row used to be plain concatenation done inside {@link
 * HudRenderer} ({@code label + ": " + value + cursor}), measured by nobody, and a real API URL is
 * comfortably wider than the modal, so it was drawn through the right border and across whatever
 * was behind it.
 *
 * <p>Each test first asserts that its fixture really is too long for the modal. That check is not
 * ceremony: the composition it names is character-for-character what the renderer used to draw, so
 * it states the defect in the same file that shows the fix, and a fixture that quietly became
 * short enough would otherwise leave the test passing while proving nothing.
 */
class SettingsModalLayoutTest {

    private static final String LONG_URL = "https://api.github.com/repos/octocat/hello-world/commits?per_page=100";

    @Test
    void anOverlongUrlKeepsItsEndSoWhatIsBeingTypedStaysVisible() {
        assertTrue(HudText.widthOf("URL: " + LONG_URL + "_") > SettingsModalLayout.ROW_WIDTH,
                "fixture is not actually too long for the modal — the test would prove nothing");

        String row = SettingsModalLayout.fieldRow("URL", LONG_URL, true);

        assertTrue(HudText.widthOf(row) <= SettingsModalLayout.ROW_WIDTH,
                "the row overflows the modal by " + (HudText.widthOf(row) - SettingsModalLayout.ROW_WIDTH) + " px: " + row);
        assertTrue(row.startsWith("URL: "), "the field must still say which field it is: " + row);
        assertTrue(row.endsWith("per_page=100_"),
                "keystrokes land at the END of the buffer, so the end is the part that must stay on screen: " + row);
    }

    /** The cursor marks the focused field and must not be what gets cut off — it is drawn last, past the value. */
    @Test
    void onlyTheFocusedFieldCarriesTheCursorAndItSurvivesTheCut() {
        String focused = SettingsModalLayout.fieldRow("URL", LONG_URL, true);
        String unfocused = SettingsModalLayout.fieldRow("URL", LONG_URL, false);

        assertTrue(focused.endsWith("_"), "the focused field is the one taking keystrokes: " + focused);
        assertTrue(!unfocused.endsWith("_"), "an unfocused field must not look like it has the caret: " + unfocused);
        assertTrue(HudText.widthOf(focused) <= SettingsModalLayout.ROW_WIDTH, "the cursor pushed the row over the edge: " + focused);
    }

    /**
     * A label long enough to fill the row on its own is capped, not honoured — labels come from
     * {@code EditableBuilding}, so a mod writes them, and a row whose label crowded out the value
     * would hide the one thing the player is editing.
     */
    @Test
    void aVerboseLabelCannotCrowdTheValueOutOfItsOwnRow() {
        String label = "Endpoint the miner polls on every request interval";
        assertTrue(HudText.widthOf(label + ": ") > SettingsModalLayout.ROW_WIDTH / 2,
                "fixture label is not long enough to test the cap");

        String row = SettingsModalLayout.fieldRow(label, LONG_URL, true);

        assertTrue(HudText.widthOf(row) <= SettingsModalLayout.ROW_WIDTH, "the row overflows the modal: " + row);
        assertTrue(row.endsWith("per_page=100_"), "the value's end must survive a greedy label: " + row);
    }

    /** A row nobody edits keeps its beginning: its meaning is at the front, unlike a field being typed into. */
    @Test
    void aRowNobodyEditsKeepsItsBeginningInsteadOfItsEnd() {
        String hint = "Tab switches fields, Enter applies the change, Esc closes without saving anything";
        assertTrue(HudText.widthOf(hint) > SettingsModalLayout.ROW_WIDTH, "fixture is not actually too long");

        String row = SettingsModalLayout.readOnlyRow(hint);

        assertTrue(HudText.widthOf(row) <= SettingsModalLayout.ROW_WIDTH, "the row overflows the modal: " + row);
        assertTrue(row.startsWith("Tab switches fields"), "the front of a read-only line is the part worth keeping: " + row);
        assertTrue(row.endsWith("..."), "the cut must be visible: " + row);
    }

    /** A value that fits is left alone — no ellipsis, no scrolling, nothing to explain to the player. */
    @Test
    void aValueThatFitsIsLeftExactlyAsItIs() {
        assertEquals("URL: http://localhost:8080_", SettingsModalLayout.fieldRow("URL", "http://localhost:8080", true));
    }
}
