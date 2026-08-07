package com.graphics.render;

/**
 * Content-agnostic geometry for the generic settings modal — same split as {@link
 * InspectionPanelLayout} (pure math here, drawing in {@link HudRenderer}, hit-testing in {@code
 * com.graphics.input.SettingsModal}) and, deliberately, the same {@link
 * InspectionPanelLayout#LINE_HEIGHT} — one row height convention for every panel in this game
 * rather than a second number to keep in sync by hand.
 *
 * <p>Centered on screen, not tucked in a corner like the inspection panel: {@code
 * com.graphics.input.SettingsModal} intercepts ALL other input while open (see its own javadoc),
 * the same "the rest of the game is frozen behind this" contract a real modal dialog makes — a
 * player expects it in the middle, not off to one side where it could be missed.
 */
public final class SettingsModalLayout {

    private static final float PANEL_WIDTH = 380f;
    private static final float TITLE_HEIGHT = 26f;
    /** Above the title and below the last row — room for the "Tab/Enter/Esc" hint line. */
    private static final float TOP_PADDING = 12f;
    private static final float BOTTOM_PADDING = 30f;

    private SettingsModalLayout() {
    }

    static float panelWidth() {
        return PANEL_WIDTH;
    }

    /** {@code readOnlyCount + fieldCount} is the same "how many rows" both {@link HudRenderer} and the hit-tester need — never computed two different ways. */
    static float panelHeight(int readOnlyCount, int fieldCount) {
        return TITLE_HEIGHT + TOP_PADDING + (readOnlyCount + fieldCount) * InspectionPanelLayout.LINE_HEIGHT + BOTTOM_PADDING;
    }

    static float panelX(int screenWidth) {
        return (screenWidth - PANEL_WIDTH) / 2f;
    }

    static float panelY(int screenHeight, int readOnlyCount, int fieldCount) {
        return (screenHeight - panelHeight(readOnlyCount, fieldCount)) / 2f;
    }

    /** Screen Y (top-down, {@code Gdx.input}'s own convention) of the FIRST editable field's row — read-only lines and the title sit above it. */
    static float firstFieldScreenY(int screenHeight, int readOnlyCount, int fieldCount) {
        float panelY = panelY(screenHeight, readOnlyCount, fieldCount);
        float panelH = panelHeight(readOnlyCount, fieldCount);
        float hudYOfFirstField = panelY + panelH - TITLE_HEIGHT - TOP_PADDING - readOnlyCount * InspectionPanelLayout.LINE_HEIGHT;
        return screenHeight - hudYOfFirstField;
    }

    /** Whether {@code (screenX, screenY)} lands anywhere inside the panel — swallows a click that hit the modal but no specific field row, same reason {@link InspectionPanelLayout#isOverPanel} exists. */
    static boolean isOverPanel(float screenX, float screenY, int screenWidth, int screenHeight, int readOnlyCount, int fieldCount) {
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(readOnlyCount, fieldCount);
        float panelY = panelY(screenHeight, readOnlyCount, fieldCount);
        float hudY = screenHeight - screenY;
        return screenX >= panelX && screenX <= panelX + PANEL_WIDTH && hudY >= panelY && hudY <= panelY + panelH;
    }

    /**
     * Which field index (0-based) sits under {@code (screenX, screenY)}, or -1 if the click missed
     * every field row (hit the title, a read-only line, the hint, or missed the panel outright).
     * Public — {@code com.graphics.input.SettingsModal} needs this to turn a click into a focus
     * change, the same reason {@link InspectionPanelLayout#hitTestRecipe} is public.
     */
    public static int hitTestField(float screenX, float screenY, int screenWidth, int screenHeight, int readOnlyCount, int fieldCount) {
        if (!isOverPanel(screenX, screenY, screenWidth, screenHeight, readOnlyCount, fieldCount)) {
            return -1;
        }
        float firstFieldTop = firstFieldScreenY(screenHeight, readOnlyCount, fieldCount);
        if (screenY < firstFieldTop) {
            return -1;
        }
        int row = (int) Math.floor((screenY - firstFieldTop) / InspectionPanelLayout.LINE_HEIGHT);
        return row >= 0 && row < fieldCount ? row : -1;
    }
}
