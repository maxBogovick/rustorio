package com.graphics.render;

import com.graphics.GfxConfig;

/**
 * Pure arithmetic layer under {@link GameCamera}: viewport size, zoom, pan, and the
 * screen-pixel-to-world-cell math ({@link #unproject}/{@link #visibleTiles}/{@link #pickTile}) —
 * no libGDX types at all, not even {@code OrthographicCamera}/{@code Matrix4} (A1,
 * CODE_REVIEW_2026-07-28.md, owner decision (б)).
 *
 * <p><b>Why this exists as its own class.</b> {@code GameCamera} itself can't be constructed
 * headless — not because it draws anything, but because {@code OrthographicCamera.update()}
 * (called from every mutator) reaches a genuinely {@code native} method ({@code Matrix4.prj}),
 * which throws {@code UnsatisfiedLinkError} outside a real windowed run: the natives library is
 * {@code runtimeOnly} in {@code build.gradle} and never lands on the test classpath. Every formula
 * in THIS class, by contrast, was already hand-rolled arithmetic before this extraction — {@code
 * GameCamera}'s own {@code unproject} javadoc already explains why it avoids {@code
 * cam.unproject}. Moving that arithmetic here, byte-for-byte unchanged, doesn't change behavior;
 * it just gives it an address a JUnit test can reach without a GPU.
 */
final class CameraViewport {

    /** Same floor as {@link GameCamera}'s own — see that field's javadoc for why it can go negative without this. */
    private static final float MIN_VIEWPORT_HEIGHT = 64f;

    private final float worldW;
    private final float worldH;
    private final int gridH;

    private float viewportWidth;
    private float viewportHeight;
    private float zoom = 1f;
    private float x;
    private float y;

    CameraViewport(int gridW, int gridH) {
        this.gridH = gridH;
        this.worldW = gridW * GfxConfig.TILE;
        this.worldH = gridH * GfxConfig.TILE;
        resize(GfxConfig.WINDOW_W, GfxConfig.WINDOW_H);
        // Стартуем над левым верхним углом карты — тот же порядок, что GameCamera's constructor.
        this.x = viewportWidth / 2f;
        this.y = worldH - viewportHeight / 2f;
        clamp();
    }

    float x() {
        return x;
    }

    float y() {
        return y;
    }

    float zoom() {
        return zoom;
    }

    float viewportWidth() {
        return viewportWidth;
    }

    float viewportHeight() {
        return viewportHeight;
    }

    void resize(int width, int height) {
        viewportWidth = Math.max(1f, width);
        viewportHeight = Math.max(MIN_VIEWPORT_HEIGHT, height - GfxConfig.HUD_TOP_HEIGHT - GfxConfig.HUD_BOTTOM_HEIGHT);
        clamp();
    }

    void pan(float dx, float dy) {
        x += dx * zoom;
        y += dy * zoom;
        clamp();
    }

    void zoomAt(float screenX, float screenY, float steps) {
        float anchorX = unprojectX(screenX);
        float anchorY = unprojectY(screenY);
        float factor = (float) Math.pow(GfxConfig.CAMERA_ZOOM_STEP, steps);
        zoom = clampValue(zoom * factor, GfxConfig.CAMERA_ZOOM_MIN, GfxConfig.CAMERA_ZOOM_MAX);
        x += anchorX - unprojectX(screenX);
        y += anchorY - unprojectY(screenY);
        clamp();
    }

    /** Same contract as {@link GameCamera#visibleTiles} — see that method's javadoc. */
    TileRange visibleTiles(int gridW) {
        float halfW = viewportWidth * zoom / 2f;
        float halfH = viewportHeight * zoom / 2f;
        int minX = (int) Math.floor((x - halfW) / GfxConfig.TILE);
        int maxX = (int) Math.floor((x + halfW) / GfxConfig.TILE);
        int minY = gridH - 1 - (int) Math.floor((y + halfH) / GfxConfig.TILE);
        int maxY = gridH - 1 - (int) Math.floor((y - halfH) / GfxConfig.TILE);
        return new TileRange(
                Math.max(minX, 0), Math.max(minY, 0),
                Math.min(maxX, gridW - 1), Math.min(maxY, gridH - 1));
    }

    /** Same contract as {@link GameCamera#pickTile} — see that method's javadoc. */
    TilePos pickTile(float screenX, float screenY) {
        int gx = (int) Math.floor(unprojectX(screenX) / GfxConfig.TILE);
        int gy = gridH - 1 - (int) Math.floor(unprojectY(screenY) / GfxConfig.TILE);
        return new TilePos(gx, gy);
    }

    /** The world-plane X counterpart to {@link #unprojectY} — see {@link GameCamera#unproject}'s own javadoc for the formula's reasoning. */
    private float unprojectX(float screenX) {
        return x + (screenX - viewportWidth / 2f) * zoom;
    }

    /** {@code screenY} is raw {@code Gdx.input} Y (from the top of the WHOLE window) — see {@link GameCamera#unproject}. */
    private float unprojectY(float screenY) {
        float viewportY = screenY - GfxConfig.HUD_TOP_HEIGHT;
        return y + (viewportHeight / 2f - viewportY) * zoom;
    }

    private void clamp() {
        float halfW = viewportWidth * zoom / 2f;
        float halfH = viewportHeight * zoom / 2f;
        x = axisClamp(x, halfW, worldW);
        y = axisClamp(y, halfH, worldH);
    }

    private static float axisClamp(float pos, float halfView, float worldSpan) {
        if (halfView * 2 >= worldSpan) {
            return worldSpan / 2f; // вид шире мира — центрируем
        }
        return clampValue(pos, halfView, worldSpan - halfView);
    }

    private static float clampValue(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
