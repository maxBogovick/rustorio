package com.graphics.render;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Everything the HUD (and, since F-02, the world-layer build ghost) needs to draw itself that
 * isn't the world or the production log: what's selected to build, which way it's facing, whether
 * the sim is paused, how fast it's running, whether the recipe book, tech tree or stats screen is
 * open, the tiles touched so far by an in-progress build drag, which cell's inspection panel (if
 * any) is open, whether Alt is currently held down, and which item the stats screen is graphing.
 *
 * <p>Replaces eight parameters {@link Renderer#render} used to thread through by hand — three of
 * them bare {@code boolean}/{@code int}, indistinguishable from each other at a call site without
 * counting positions. Every new HUD toggle used to mean editing four signatures ({@code
 * InputHandler} → {@code GameScreen} → {@code Renderer} → {@code HudRenderer}); now it's one new
 * record component. See P3-05, BUG_FIX_PROGRESS.md.
 *
 * <p>{@code dragTiles}/{@code inspected}/{@code altOverlay}/{@code statsItem} ride along here
 * rather than as their own {@link Renderer#render} parameters (F-02/F-03/F-04/P-03, DEV_TASKS.md)
 * for the same reason: all four originate in {@code InputHandler}, same as {@code selected}/{@code
 * facing}, and the render layer needs them alongside those — {@code dragTiles} empty when nothing
 * is being dragged, {@code inspected} null when no panel is open, {@code altOverlay} true only
 * while Alt is held down, {@code statsItem} which {@link ItemType} the {@code N} key has currently
 * selected to graph on the stats screen (meaningless while {@code showStats} is false).
 */
public record HudState(BuildingType selected, Direction facing, boolean paused, int speed, boolean showRecipeBook,
        boolean showTechTree, List<TilePos> dragTiles, @Nullable TilePos inspected, boolean altOverlay,
        boolean showStats, ItemType statsItem) {
}
