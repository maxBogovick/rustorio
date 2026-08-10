package com.graphics.render;

import com.rustorio.api.content.ContentId;
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
 *
 * <p>{@code selected} is a {@link ContentId} — any registered building prototype, vanilla or
 * modded, not just the closed {@code BuildingType} set. {@code hotbarSlots} is the
 * player's own configurable hotbar — see {@code InputHandler}'s own field for what populates it —
 * read here so {@code HudRenderer}/{@code OverlayRenderer} never need a second way to ask "what's
 * pinned to slot N."
 *
 * <p>{@code buildMenuQuery}/{@code buildMenuCategoryCycle}/{@code buildMenuScrollOffset} are
 * meaningless while {@code showBuildMenu} is {@code false} — {@code BuildMenuRenderer} owns
 * turning the raw cycle/scroll counters into an actual category and page (it's the one place that
 * knows how many namespaces/matches are registered right now; see {@code SimulationControls}'s own
 * fields for why the counters themselves don't know).
 *
 * <p>{@code pageView} is {@code null} unless a building's own picture is open full-screen (a
 * monitor's fetched page, today) — see {@code ViewableBuilding} for why the HUD carries pixels it
 * knows nothing about, and {@link PageView} for why the image and its scroll travel together.
 *
 * <p>{@code statusMessage} is {@code null} outside the few seconds right after F5/F9 — a live bug
 * report: pressing save/load gave the player NO on-screen feedback at all, success or failure
 * (only a {@code System.Logger} line {@code InputHandler} wrote on failure, invisible in a
 * windowed run started any way other than from a terminal); a player had no way to tell whether F5
 * had actually done anything.
 *
 * <p>{@code showInfo} (I key) is a fifth {@code SimulationControls.OverlayPanel}, same mutual
 * exclusivity as recipe book/tech tree/stats/build menu — everything the compact top strip used to
 * cram into eight permanent rows (produced totals, inventory, research, recent log, full alert
 * breakdown) now lives here instead, on demand (HUD redesign, live design feedback: the always-on
 * panel read as a cluttered debug console). {@code showHints}/{@code showFpsUps} (H/P) are NOT
 * panels — independent booleans, since neither is full-screen and either can be on alongside any
 * panel or the other: {@code showHints} swaps the top strip's one-line hotkey reminder for the old
 * full three-line legend, {@code showFpsUps} shows the FPS/UPS line at all (previously always on,
 * now opt-in — most players never need it).
 */
public record HudState(ContentId selected, Direction facing, boolean paused, int speed, boolean showRecipeBook,
        boolean showTechTree, List<TilePos> dragTiles, @Nullable TilePos inspected, boolean altOverlay,
        boolean showStats, ItemType statsItem, List<ContentId> hotbarSlots, boolean showBuildMenu,
        String buildMenuQuery, int buildMenuCategoryCycle, int buildMenuScrollOffset, @Nullable String statusMessage,
        boolean showInfo, boolean showHints, boolean showFpsUps, @Nullable SettingsModalView settingsModal,
        int activeCategoryIndex, @Nullable ContentId hoveredPrototype, DisplayLabels displayLabels,
        @Nullable PageView pageView) {
}
