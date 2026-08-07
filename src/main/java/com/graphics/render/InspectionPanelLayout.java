package com.graphics.render;

import com.graphics.GfxConfig;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.RecipeSelectable;
import com.rustorio.domain.building.Splitter;
import com.rustorio.domain.building.UndergroundBelt;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Content AND geometry for the inspection panel — one formula shared by drawing ({@link
 * HudRenderer}) and hit-testing ({@code com.graphics.input.InputHandler}, a different package —
 * same reason {@link BuildMenuLayout}/{@link QuickBarLayout} are public). The panel itself is just a
 * top-to-bottom list of text lines ({@link #inspectionLines}); {@link #clickableRecipes} names the
 * trailing lines of that list a click can act on — {@link #appendFurnaceDetails} always appends one
 * line per {@link Furnace#possibleRecipes()} entry LAST, in that exact order, so "the last {@code
 * clickableRecipes(building).size()} lines" needs no separate index to track.
 */
public final class InspectionPanelLayout {

    static final float LINE_HEIGHT = 18f;
    static final float PANEL_WIDTH = 340f; // wide enough for a two-input recipe line ("IRON_ORE + BRONZE_PLATE -> ALLOY_PLATE")

    /** Roughly how many fixed-width HUD glyphs fit across {@link #PANEL_WIDTH} — see {@link #wrapToPanel} for why this counts characters instead of measuring the font. */
    private static final int MAX_LINE_CHARS = 56;
    /** How many rows ONE over-long fact may occupy before it is cut — six keeps a raw response body readable without letting it push a chest's contents off the panel. */
    private static final int MAX_WRAPPED_ROWS = 6;
    private static final String ELLIPSIS = "...";

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
        // Rows tile evenly from the panel's TOP edge downward — a few px looser than the actual
        // glyph baseline HudRenderer draws at (which sits a little lower, for legibility), but that
        // only widens each row's own click target, never lets one row's click land on a neighbor.
        int line = (int) Math.floor((panelY + panelH - hudY) / LINE_HEIGHT);
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

        // BEFORE the kind-specific branches below, not after, and the reason is load-bearing:
        // appendFurnaceDetails must keep emitting the recipe rows LAST, because hitTestRecipe finds
        // a clicked recipe by counting back from the end of the list ("the last
        // clickableRecipes(building).size() lines"). Appending anything after it would silently
        // shift every recipe's click target — and a building that is both RecipeSelectable and
        // InspectableBuilding is allowed, so "no archetype does that today" is not a guarantee.
        if (building instanceof InspectableBuilding inspectable) {
            for (String line : inspectable.inspectionDetails(world, at.x(), at.y())) {
                lines.addAll(wrapToPanel(line));
            }
        }

        // The five branches below are vanilla archetypes that predate InspectableBuilding; they are
        // not a pattern to extend, and a NEW building describes itself through that interface
        // instead — which is what lets a mod's own archetype appear here with no edit to this file.
        if (building instanceof Chest chest) {
            appendChestContents(lines, items, chest);
        } else if (building instanceof Furnace furnace) {
            appendFurnaceDetails(lines, world, building, furnace);
        } else if (building instanceof UndergroundBelt tunnel) {
            appendTunnelPairing(lines, world, at, building, tunnel);
        } else if (building instanceof Filter filter) {
            lines.add("Passes forward: " + filter.filterItem().label() + "  (F to change)");
            lines.add("Everything else -> secondary side");
        } else if (building instanceof Splitter) {
            lines.add("Round-robin: alternates forward / secondary side");
        }
        return lines;
    }

    /**
     * {@code line} split across as many panel-width rows as it needs, capped so one very long line
     * (a raw HTTP response body, say) can't push everything else off the screen. Applied to what
     * {@link InspectableBuilding} returns and nothing else: a building states a FACT, and how wide
     * a fact may be drawn is knowledge only this class has.
     *
     * <p>Character count, not measured glyph width — the HUD font here is fixed-width, and a
     * layout that had to measure text would need the {@code BitmapFont} itself, which would drag a
     * libGDX type into the one class in this package that is deliberately pure enough to unit-test
     * without a GL context.
     */
    private static List<String> wrapToPanel(String line) {
        if (line.length() <= MAX_LINE_CHARS) {
            return List.of(line);
        }
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < line.length() && rows.size() < MAX_WRAPPED_ROWS; i += MAX_LINE_CHARS) {
            rows.add(line.substring(i, Math.min(i + MAX_LINE_CHARS, line.length())));
        }
        if (line.length() > MAX_LINE_CHARS * MAX_WRAPPED_ROWS) {
            int last = rows.size() - 1;
            rows.set(last, rows.get(last).substring(0, MAX_LINE_CHARS - ELLIPSIS.length()) + ELLIPSIS);
        }
        return rows;
    }

    private static void appendChestContents(List<String> lines, Registry<ItemType> items, Chest chest) {
        boolean any = false;
        for (ItemType item : items.iterate()) {
            int amount = chest.amount(item);
            if (amount > 0) {
                lines.add("  " + item.label() + ": " + amount);
                any = true;
            }
        }
        if (!any) {
            lines.add("  (empty)");
        }
    }

    /**
     * The full recipe — input(s) AND output, not just the output {@link Recipe#output()} —
     * because that's the actual live bug report: the old panel showed "-> IRON_PLATE" and nothing
     * about what to feed it. Every recipe this furnace's kind can run at all is ALWAYS listed last
     * (not only while nothing's committed yet), each as its own line — a live bug report of its
     * own: the player had no way to see, let alone pick, an alternative once one was already
     * selected. This method only builds the TEXT; {@link #clickableRecipes} names these exact same
     * trailing lines as the click targets {@code InputHandler} acts on.
     */
    private static void appendFurnaceDetails(List<String> lines, World world, Building building, Furnace furnace) {
        Optional<Recipe> active = furnace.activeRecipe();
        if (active.isPresent()) {
            lines.add("Recipe: " + recipeLine(active.get()) + "  (cooking)");
        } else {
            Optional<Recipe> selected = furnace.selectedRecipeChoice();
            lines.add(selected.isPresent()
                    ? "Recipe: " + recipeLine(selected.get()) + "  (selected)"
                    : "Recipe: none committed yet");
        }
        lines.add("Ore buffer: " + furnace.oreBuffer());
        // Whether this building BURNS anything is data on its own prototype, not a vanilla
        // constant — sandbox:voron declares coal as fuel and never showed this line.
        if (world.buildingFactory().prototype(building.prototypeId()).fuelItem() != null) {
            lines.add("Fuel: " + furnace.fuelBuffer());
        }
        lines.add("Click a recipe to select it:");
        for (Recipe recipe : furnace.possibleRecipes()) {
            lines.add("  " + recipeLine(recipe));
        }
    }

    private static String recipeLine(Recipe recipe) {
        String inputs = recipe.ingredients().stream().map(ItemType::label).collect(Collectors.joining(" + "));
        return inputs + " -> " + recipe.output().label();
    }

    private static void appendTunnelPairing(List<String> lines, World world, TilePos at, Building building,
            UndergroundBelt tunnel) {
        if (!tunnel.isEntrance()) {
            lines.add("(exit — pairing shown at its entrance)");
            return;
        }
        boolean paired = tunnel.findPartner(world, at.x(), at.y()).isPresent();
        lines.add("Paired: " + (paired ? "yes" : "NO — out of range or no matching exit"));
    }
}
