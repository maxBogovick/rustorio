package com.graphics.render;

import com.graphics.GfxConfig;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.model.Recipe;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.RecipeSelectable;
import com.rustorio.domain.building.ViewableBuilding;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Content AND geometry for the inspection panel — one formula shared by drawing ({@link
 * HudRenderer}) and hit-testing ({@code com.graphics.input.InputHandler}, a different package —
 * same reason {@link BuildMenuLayout}/{@link QuickBarLayout} are public). The panel itself is just a
 * top-to-bottom list of text lines ({@link #inspectionLines}); {@link #clickableRecipes} names the
 * trailing lines of that list a click can act on — a {@link RecipeSelectable} that is also {@link
 * InspectableBuilding} always appends one line per recipe LAST inside {@link
 * InspectableBuilding#inspectionDetails}, in that exact order, so "the last {@code
 * clickableRecipes(building).size()} lines" needs no separate index to track.
 */
public final class InspectionPanelLayout {

    static final float LINE_HEIGHT = 18f;
    static final float PANEL_WIDTH = 340f; // wide enough for a two-input recipe line ("IRON_ORE + BRONZE_PLATE -> ALLOY_PLATE")
    /** Left inset of every text row, and the matching gap kept on the right — {@link HudRenderer} draws at this offset, {@link #wrapToPanel} wraps to what is left over. */
    static final float TEXT_PAD = 12f;
    /**
     * How far below the panel's top edge the FIRST row of text is drawn. Shared with {@link
     * HudRenderer} deliberately, because drawing and hit-testing disagreeing about it was a live
     * bug: rows were hit-tested as if they began at the very top edge while being drawn 14 px
     * lower, so of the ~15 px a line of glyphs occupies, only its top 4 px landed inside its own
     * band and the other 11 landed in the band below. Clicking a word selected the row UNDER it,
     * clicking the last row selected nothing at all, and the way to hit a row was to click above
     * its text — which is exactly what a player reported. The furnace recipe picker had it too.
     */
    static final float FIRST_ROW_TOP = 14f;
    /**
     * The row that opens a building's own picture full-screen ({@code ViewableBuilding}) — a
     * monitor's fetched page, today.
     *
     * <p>Appended LAST, and only when this building has no clickable recipes: {@link
     * #hitTestRecipe} finds a recipe by counting back from the end, so a row after the recipe
     * section would silently shift every recipe's click target. Nothing in the game is both today;
     * the guard is here so that stops being a thing anyone has to remember.
     */
    static final String OPEN_PAGE_ROW = "[ Open page ]";

    /** How many rows ONE over-long fact may occupy before it is cut — six keeps a raw response body readable without letting it push a chest's contents off the panel. */
    private static final int MAX_WRAPPED_ROWS = 6;
    /** Pixels a row may fill: the panel minus the inset on both sides. Package-private so a test can assert the invariant itself ("no row is wider than this") rather than a hand-copied character count that goes stale. */
    static final float ROW_WIDTH = PANEL_WIDTH - 2 * TEXT_PAD;

    private InspectionPanelLayout() {
    }

    /** Every recipe the inspection panel lists as a clickable row for {@code building}, in the exact order rendered — empty for anything that doesn't implement {@link RecipeSelectable}. */
    public static List<Recipe> clickableRecipes(Building building) {
        return building instanceof RecipeSelectable selectable ? selectable.possibleRecipes() : List.of();
    }

    static float panelHeight(int lineCount) {
        return 20f + lineCount * LINE_HEIGHT;
    }

    static float panelX(int screenWidth) {
        return screenWidth - PANEL_WIDTH - 16f;
    }

    static float panelY(int screenHeight, int lineCount) {
        return screenHeight - GfxConfig.HUD_TOP_HEIGHT - 16f - panelHeight(lineCount);
    }

    /**
     * Which line index (0-based, top to bottom — same order as {@link HudRenderer#inspectionLines})
     * sits under {@code (screenX, screenY)} — {@code Gdx.input}'s screen coordinates (Y from the
     * top), same flip {@link BuildMenuLayout}/{@link QuickBarLayout} already do — or {@code -1} if the
     * click missed the panel, or landed in its own bottom padding below the last line.
     */
    static int hitTestLine(float screenX, float screenY, int screenWidth, int screenHeight, int lineCount) {
        if (lineCount <= 0) {
            return -1;
        }
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(lineCount);
        float panelY = panelY(screenHeight, lineCount);
        if (screenX < panelX || screenX > panelX + PANEL_WIDTH) {
            return -1;
        }
        float hudY = screenHeight - screenY;
        if (hudY < panelY || hudY > panelY + panelH) {
            return -1;
        }
        // Bands start where the TEXT starts, not at the panel's top edge — see FIRST_ROW_TOP for
        // what the old version did instead and how it read to a player. Row i's glyphs hang below
        // their own top, so the band is the LINE_HEIGHT below it.
        int line = (int) Math.floor((panelY + panelH - FIRST_ROW_TOP - hudY) / LINE_HEIGHT);
        return line >= 0 && line < lineCount ? line : -1;
    }

    /**
     * Which of {@link #clickableRecipes}, if any, sits under {@code (screenX, screenY)} right now —
     * the one call {@code InputHandler} needs, combining {@link #hitTestLine} with "recipe rows are
     * always the last N lines" so it never has to know the panel's line-numbering scheme itself.
     */
    public static Optional<Recipe> hitTestRecipe(float screenX, float screenY, int screenWidth,
            int screenHeight, int totalLineCount, List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            return Optional.empty();
        }
        int line = hitTestLine(screenX, screenY, screenWidth, screenHeight, totalLineCount);
        int recipeSectionStart = totalLineCount - recipes.size();
        if (line < recipeSectionStart) {
            return Optional.empty();
        }
        return Optional.of(recipes.get(line - recipeSectionStart));
    }

    /**
     * Whether {@code (screenX, screenY)} hit the row that opens this building's picture — the LAST
     * row, and present only under the conditions {@link #OPEN_PAGE_ROW} documents. Public for the
     * same reason {@link #hitTestRecipe} is: the click arrives in {@code com.graphics.input}.
     */
    public static boolean hitTestOpenPage(float screenX, float screenY, int screenWidth, int screenHeight,
            List<String> lines) {
        if (lines.isEmpty() || !lines.getLast().equals(OPEN_PAGE_ROW)) {
            return false;
        }
        return hitTestLine(screenX, screenY, screenWidth, screenHeight, lines.size()) == lines.size() - 1;
    }

    /**
     * Whether {@code (screenX, screenY)} lands anywhere inside the panel at all — swallows a click
     * that hit the panel but no specific recipe row, so it doesn't leak into the world tile behind
     * it (same reason {@link BuildMenuLayout#isOverPanel} exists). {@code lineCount <= 0} means no
     * panel is even open right now.
     */
    public static boolean isOverPanel(float screenX, float screenY, int screenWidth, int screenHeight, int lineCount) {
        if (lineCount <= 0) {
            return false;
        }
        float panelX = panelX(screenWidth);
        float panelH = panelHeight(lineCount);
        float panelY = panelY(screenHeight, lineCount);
        float hudY = screenHeight - screenY;
        return screenX >= panelX && screenX <= panelX + PANEL_WIDTH && hudY >= panelY && hudY <= panelY + panelH;
    }

    /**
     * One line per fact — kind-specific extras appended after the facts every building shares.
     * Public on purpose: both {@link HudRenderer} (drawing) and {@code
     * com.graphics.input.InputHandler} (hit-testing a click) need this exact same line count/order
     * — see this class's own javadoc for why they must never compute it two different ways.
     *
     * <p>Every line returned fits the panel, and {@link #fitToPanel} is the single place that makes
     * that true — not each caller, and not only the facts a mod supplies, which is as far as the
     * fitting used to reach. The lines assembled here are just as capable of overflowing: a modded
     * building's label, a modded item's name in a chest listing, and a recipe naming two of them
     * are all text this file does not control the length of.
     */
    public static List<String> inspectionLines(World world, Registry<ItemType> items, TilePos at, Building building) {
        List<String> lines = new ArrayList<>();
        // The PROTOTYPE's label, not the borrowed archetype's: a modded building showed the
        // vanilla kind it reuses ("Miner") instead of its own name.
        lines.add(world.buildingFactory().prototype(building.prototypeId()).label()
                + "  (" + at.x() + ", " + at.y() + ")");
        lines.add("Status: " + building.appearance().status());
        if (building.speedLevel() > 0) {
            lines.add("Speed modules: x" + building.speedLevel());
        }
        building.heldItem().ifPresent(item -> lines.add("Holding: " + item.label()));

        // RecipeSelectable buildings that are also InspectableBuilding put their recipe picker rows
        // LAST inside inspectionDetails — hitTestRecipe finds a clicked recipe by counting back
        // from the end of the list. Appending anything after those rows (except the open-page row,
        // which is gated on recipes.isEmpty()) would silently shift every recipe's click target.
        if (building instanceof InspectableBuilding inspectable) {
            lines.addAll(inspectable.inspectionDetails(world, at.x(), at.y()));
        }

        List<Recipe> recipes = clickableRecipes(building);
        if (building instanceof ViewableBuilding && recipes.isEmpty()) {
            lines.add(OPEN_PAGE_ROW);
        }
        return fitToPanel(lines, recipes.size());
    }

    /**
     * Every line above, laid out so none is drawn wider than the panel: ordinary lines wrap onto
     * however many rows they need, the trailing {@code recipeCount} recipe rows are cut to one row
     * each instead.
     *
     * <p>The asymmetry is not a taste call — it is what keeps {@link #hitTestRecipe} honest. That
     * method finds a clicked recipe by counting back from the end ("the last N lines ARE the N
     * recipes"), so a recipe row that wrapped onto two rows would silently shift every recipe's
     * click target by one. A cut row still reads: the recipe list is a picker, and the player
     * already knows which recipes their furnace can run.
     */
    private static List<String> fitToPanel(List<String> lines, int recipeCount) {
        List<String> fitted = new ArrayList<>(lines.size());
        int recipeSectionStart = lines.size() - recipeCount;
        for (int i = 0; i < lines.size(); i++) {
            if (i < recipeSectionStart) {
                fitted.addAll(wrapToPanel(lines.get(i)));
            } else {
                fitted.add(fitToOneRow(lines.get(i)));
            }
        }
        return fitted;
    }

    /** {@code line} as a single row, cut with an ellipsis if it doesn't fit — visible, not silent. */
    private static String fitToOneRow(String line) {
        return HudText.keepStart(line, ROW_WIDTH);
    }

    /**
     * One fact laid out across the panel's own width, capped at {@link #MAX_WRAPPED_ROWS} rows so a
     * raw HTTP response body can't push everything else off the screen. The measuring and the
     * whitespace rules are {@link HudText}'s — shared with the settings modal, which had the same
     * overflow for the same reason; this class only says how wide its own rows are and how many of
     * them one fact may have.
     */
    private static List<String> wrapToPanel(String fact) {
        return HudText.wrap(fact, ROW_WIDTH, MAX_WRAPPED_ROWS);
    }
}
