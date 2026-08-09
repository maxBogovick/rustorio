package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.QuickBarLayout;
import com.graphics.render.TilePos;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Collects the tiles touched by one held mouse button into a single drag gesture — shared logic
 * behind {@code InputHandler}'s build-drag (LMB) and remove-drag (RMB), which used to be two
 * near-identical 30-line methods differing only in which button and which {@code PlayerAction}
 * they fed into {@code CompositeAction}. This class owns exactly the part that was duplicated:
 * "which tiles did the button touch since it was pressed"; {@code InputHandler} still decides what
 * to build out of the answer.
 */
final class DragCollector {

    private final int button;
    private final List<TilePos> tiles = new ArrayList<>();
    private boolean dragging;
    /** True if THIS press started anywhere a world gesture must not begin — see {@link #blocksGestureStart} for the three independent reasons. */
    private boolean blocked;

    DragCollector(int button) {
        this.button = button;
    }

    /**
     * Feed one frame of input. Returns the tiles touched by the gesture exactly once — the frame
     * the button is released after a drag that was allowed to begin at all ({@link
     * #blocksGestureStart}) — and {@code null} every other frame (still held, or nothing worth
     * reporting). {@code hotbarSlotCount} is the CURRENT number of hotbar slots (configurable, not
     * a fixed {@code BuildingType} count); {@code alsoBlocked} lets the caller name one more
     * excluded region (the inspection panel's recipe picker, say) without this class needing to
     * know what it is — both read only at the moment the button goes down, same as the hotbar check
     * always was.
     *
     * <p>Where the cursor is, by contrast, is read EVERY frame: a gesture that legitimately began
     * on the map and was then dragged down onto a HUD band must not keep painting cells there,
     * because those cells are off-screen ones (see {@link com.graphics.GfxConfig#isOverWorld} for
     * what the arithmetic does with such a point) that the ghost stops drawing at that exact
     * moment. Skipping them, rather than ending the gesture: the drag is still live, and pulling
     * the cursor back up onto the map continues the same line the player was drawing.
     */
    @Nullable List<TilePos> poll(GameCamera camera, int hotbarSlotCount, boolean alsoBlocked) {
        boolean pressed = Gdx.input.isButtonPressed(button);
        if (Gdx.input.isButtonJustPressed(button)) {
            blocked = blocksGestureStart(Gdx.input.getX(), Gdx.input.getY(),
                    Gdx.graphics.getHeight(), hotbarSlotCount, alsoBlocked);
        }
        if (pressed && blocked) {
            return null;
        }
        if (pressed && !GfxConfig.isOverWorld(Gdx.input.getY(), Gdx.graphics.getHeight())) {
            return null; // gesture still live, but this frame's cell is under a panel, not under the map
        }
        if (pressed) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            if (!dragging) {
                dragging = true;
                tiles.clear();
                tiles.add(tile);
            } else if (!tile.equals(tiles.get(tiles.size() - 1))) {
                tiles.add(tile);
            }
            return null;
        }
        if (!dragging) {
            return null;
        }
        dragging = false;
        List<TilePos> collected = List.copyOf(tiles);
        tiles.clear();
        return collected;
    }

    /**
     * The tiles touched so far by a drag still IN PROGRESS — empty if not currently dragging (or
     * blocked by having started on the hotbar). Unlike {@link #poll}, this doesn't consume or
     * clear anything: it's read every frame by the build ghost (F-02, DEV_TASKS.md) to show the
     * whole planned line before release, not just the single cell under the cursor.
     */
    List<TilePos> inProgressTiles() {
        return dragging && !blocked ? List.copyOf(tiles) : List.of();
    }

    /**
     * Whether a press at this screen point must not become a world gesture at all — the whole
     * decision as one pure function, so it can be tested without a window (the {@code Gdx.input}
     * reads stay at the single call site in {@link #poll}).
     *
     * <p>Two independent reasons, and the second one is a live bug report. A press outside the
     * world viewport — anywhere in the top or bottom HUD band — used to fall straight through to
     * {@code GameCamera#pickTile}, which happily maps such a point to a map cell BEYOND the visible
     * area: clicking a category tab or a building icon in the bottom build panel both pinned the
     * prototype AND silently placed one of it a few cells below the screen edge (right-click:
     * demolished/hand-mined there). The build ghost is hidden over the HUD, so nothing on screen
     * said so — the player only found those buildings after scrolling down. See {@link
     * GfxConfig#isOverWorld}.
     *
     * <p>The quick-bar test is NOT redundant with that band check and can't be folded into it: a
     * three-row grid is taller than {@link GfxConfig#HUD_BOTTOM_HEIGHT}, so its top row sits over
     * the world viewport proper — the two rectangles overlap, neither contains the other. It asks
     * for {@code hotbarSlotCount} rather than a cell count because the grid's height follows how
     * much is pinned, so the guard has to ask the same question the renderer does or it protects a
     * rectangle that is not where the grid is. It briefly did exactly that — the old strip was
     * centred along the bottom, and after the grid moved to the corner this still guarded the
     * strip's rectangle: drags were blocked over empty screen and allowed straight over the cells.
     */
    static boolean blocksGestureStart(float screenX, float screenY, int screenHeight,
            int hotbarSlotCount, boolean alsoBlocked) {
        return alsoBlocked
                || !GfxConfig.isOverWorld(screenY, screenHeight)
                || QuickBarLayout.hitTest(screenX, screenY, screenHeight, QuickBarLayout.COLUMNS,
                        QuickBarLayout.visibleRowsFor(hotbarSlotCount)) >= 0;
    }
}
