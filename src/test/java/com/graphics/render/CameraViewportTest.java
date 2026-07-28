package com.graphics.render;

import com.graphics.GfxConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CameraViewport} — the arithmetic under {@link GameCamera} extracted specifically so it
 * can run headless (A1, CODE_REVIEW_2026-07-28.md, owner decision (б)): {@code GameCamera} itself
 * can't be constructed in a JUnit run ({@code OrthographicCamera.update()} reaches a native {@code
 * Matrix4.prj}), but every formula it delegates to this class now can be.
 */
class CameraViewportTest {

    @Test
    void visibleTilesCoversTheWholeMapWhenTheMapIsSmallerThanTheViewport() {
        // A 10x10 map (340x340 world px) is far smaller than the default 1280x528 viewport —
        // the camera can't help but see the whole thing, centered.
        CameraViewport viewport = new CameraViewport(10, 10);

        assertEquals(new TileRange(0, 0, 9, 9), viewport.visibleTiles(10));
    }

    @Test
    void panMovesThePickedTileByExactlyTheExpectedNumberOfTiles() {
        CameraViewport viewport = new CameraViewport(GfxConfig.GRID_W, GfxConfig.GRID_H);
        TilePos start = viewport.pickTile(640f, 430f); // roughly the center of the default viewport

        viewport.pan(340f, 0f); // ten tiles' worth of screen pixels, rightward
        TilePos moved = viewport.pickTile(640f, 430f);

        assertEquals(start.x() + 10, moved.x(), "panning right by 10 tiles' worth of pixels must move the picked column by exactly 10");
        assertEquals(start.y(), moved.y(), "a purely horizontal pan must not move the row");
    }

    @Test
    void panCannotPushTheCameraPastTheEdgeOfTheMap() {
        // Starts pinned to the top-left corner already (see GameCamera's own constructor javadoc)
        // — panning further up/left must not walk off the map.
        CameraViewport viewport = new CameraViewport(GfxConfig.GRID_W, GfxConfig.GRID_H);
        TilePos beforeOvershoot = viewport.pickTile(0f, GfxConfig.HUD_TOP_HEIGHT);

        viewport.pan(-100_000f, 100_000f); // absurdly large — would leave the map many times over, unclamped

        assertEquals(beforeOvershoot, viewport.pickTile(0f, GfxConfig.HUD_TOP_HEIGHT),
                "already at the corner — an overshooting pan must be fully absorbed by the clamp");
    }

    @Test
    void zoomAtKeepsTheScreenPointAnchoredToTheSameWorldTile() {
        CameraViewport viewport = new CameraViewport(GfxConfig.GRID_W, GfxConfig.GRID_H);
        // Away from the initial top-left corner first, so edge clamping can't mask a real anchor bug.
        viewport.pan(2000f, -2000f);

        TilePos before = viewport.pickTile(640f, 400f);
        viewport.zoomAt(640f, 400f, -2f); // zoom in twice, anchored at the same screen point
        TilePos after = viewport.pickTile(640f, 400f);

        assertEquals(before, after, "the tile under the cursor must not appear to move when zooming at it");
    }

    @Test
    void resizeNeverLetsTheViewportHeightGoNegative() {
        CameraViewport viewport = new CameraViewport(GfxConfig.GRID_W, GfxConfig.GRID_H);

        viewport.resize(400, 50); // far below HUD_TOP_HEIGHT + HUD_BOTTOM_HEIGHT (166 + 106 = 272)

        assertEquals(64f, viewport.viewportHeight(), "the documented MIN_VIEWPORT_HEIGHT floor");
    }
}
