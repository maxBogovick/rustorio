package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.graphics.render.GameCamera;
import com.graphics.render.HotbarLayout;
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
    /** True if THIS press started on the hotbar — a click there must never leak into the world. */
    private boolean blockedByHotbar;

    DragCollector(int button) {
        this.button = button;
    }

    /**
     * Feed one frame of input. Returns the tiles touched by the gesture exactly once — the frame
     * the button is released after a drag that didn't start on the hotbar — and {@code null}
     * every other frame (still held, or nothing worth reporting).
     */
    @Nullable List<TilePos> poll(GameCamera camera) {
        boolean pressed = Gdx.input.isButtonPressed(button);
        if (Gdx.input.isButtonJustPressed(button)) {
            blockedByHotbar = isOverHotbar();
        }
        if (pressed && blockedByHotbar) {
            return null;
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

    private static boolean isOverHotbar() {
        return HotbarLayout.hitTest(Gdx.input.getX(), Gdx.input.getY(),
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight()) >= 0;
    }
}
