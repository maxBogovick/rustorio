package com.graphics.render;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;

/**
 * Everything the HUD needs to draw itself that isn't the world or the production log: what's
 * selected to build, which way it's facing, whether the sim is paused, how fast it's running, and
 * whether the recipe book is open.
 *
 * <p>Replaces eight parameters {@link Renderer#render} used to thread through by hand — three of
 * them bare {@code boolean}/{@code int}, indistinguishable from each other at a call site without
 * counting positions. Every new HUD toggle used to mean editing four signatures ({@code
 * InputHandler} → {@code GameScreen} → {@code Renderer} → {@code HudRenderer}); now it's one new
 * record component. See P3-05, BUG_FIX_PROGRESS.md.
 */
public record HudState(BuildingType selected, Direction facing, boolean paused, int speed, boolean showRecipeBook) {
}
