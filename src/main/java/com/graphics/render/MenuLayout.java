package com.graphics.render;

/**
 * Geometry for {@link MenuRenderer}'s single-column list panel (main menu, and later the in-game
 * pause menu) — same split as {@link BuildMenuLayout}: the arithmetic lives here, libGDX-free, so
 * a JUnit test can pin down {@link #hitTestRow} without a window; drawing itself lives in {@link
 * MenuRenderer}, the only other class that needs these numbers, hence everything but {@link
 * #hitTestRow} stays package-private.
 */
public final class MenuLayout {

    static final float PADDING = 24f;
    static final float TITLE_HEIGHT = 34f;
    static final float ROW_HEIGHT = 30f;
    static final float STATUS_HEIGHT = 24f;
    static final float PANEL_WIDTH = 560f;

    private MenuLayout() {
    }

    static float panelHeight(int itemCount, boolean hasStatus) {
        int rows = Math.max(1, itemCount);
        return PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * rows + (hasStatus ? STATUS_HEIGHT : 0f);
    }

    static float panelX(int screenWidth) {
        return (screenWidth - PANEL_WIDTH) / 2f;
    }

    static float panelY(int screenHeight, int itemCount, boolean hasStatus) {
        return (screenHeight - panelHeight(itemCount, hasStatus)) / 2f;
    }

    /** Top edge (HUD Y) of row 0 — every row below sits {@link #ROW_HEIGHT} lower. */
    private static float rowTop(float panelY, float panelH) {
        return panelY + panelH - PADDING - TITLE_HEIGHT;
    }

    /** Baseline {@code y} {@link com.badlogic.gdx.graphics.g2d.BitmapFont#draw} wants for row {@code index}'s label. */
    static float rowY(float panelY, float panelH, int index) {
        return rowTop(panelY, panelH) - index * ROW_HEIGHT;
    }

    /** Bottom edge of row {@code index}'s highlight/hover band. */
    static float rowBandBottom(float panelY, float panelH, int index) {
        return rowTop(panelY, panelH) - (index + 1) * ROW_HEIGHT + (ROW_HEIGHT - TITLE_HEIGHT / 2f);
    }

    /**
     * Which row (0-based) sits under {@code (screenX, screenY)} — screen coordinates as {@code
     * Gdx.input} gives them (Y from the TOP), same convention {@link BuildMenuLayout#hitTestTile}
     * uses — or {@code -1} outside the panel or past the last item.
     */
    public static int hitTestRow(float screenX, float screenY, int screenWidth, int screenHeight, int itemCount, boolean hasStatus) {
        if (itemCount <= 0) {
            return -1;
        }
        float panelX = panelX(screenWidth);
        if (screenX < panelX || screenX > panelX + PANEL_WIDTH) {
            return -1;
        }
        float panelH = panelHeight(itemCount, hasStatus);
        float panelY = panelY(screenHeight, itemCount, hasStatus);
        float hudY = screenHeight - screenY;
        float top = rowTop(panelY, panelH);
        // Math.floor, not a bare (int) cast: a cast truncates TOWARD ZERO, which would map a point
        // just above row 0 to row 0 instead of a negative (correctly rejected) index — same reason
        // BuildMenuLayout#hitTestTile uses it.
        int index = (int) Math.floor((top - hudY) / ROW_HEIGHT);
        return index >= 0 && index < itemCount ? index : -1;
    }
}
