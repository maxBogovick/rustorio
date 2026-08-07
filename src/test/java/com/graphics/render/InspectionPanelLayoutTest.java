package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InspectionPanelLayout} — pure logic (no libGDX window needed), shared by {@link
 * HudRenderer} (drawing) and {@code com.graphics.input.InputHandler} (hit-testing a click on a
 * recipe row), same reason {@link BuildMenuLayoutTest} exists for the build menu's own geometry.
 */
class InspectionPanelLayoutTest {

    private static final RecipeBook RECIPES = RecipeBook.standard();

    @Test
    void clickableRecipesReturnsAFurnacesPossibleRecipesInOrder() {
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);

        assertEquals(furnace.possibleRecipes(), InspectionPanelLayout.clickableRecipes(furnace));
        assertFalse(InspectionPanelLayout.clickableRecipes(furnace).isEmpty(), "the vanilla FURNACE kind has real recipes to list");
    }

    @Test
    void clickableRecipesIsEmptyForABuildingThatDoesNotSelectRecipes() {
        assertTrue(InspectionPanelLayout.clickableRecipes(new Chest()).isEmpty());
    }

    /**
     * A building the panel has never heard of gets its own lines shown — the whole point of {@code
     * InspectableBuilding}. Before it, describing a building meant adding a branch to {@link
     * InspectionPanelLayout} naming that building's class, so a mod's archetype could not be
     * described at all without editing the engine.
     */
    @Test
    void aBuildingThePanelHasNeverHeardOfStillGetsItsOwnLinesShown() {
        World world = new World(4, 4);
        SelfDescribing building = new SelfDescribing(List.of("Custom: 42"));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);

        assertTrue(lines.contains("Custom: 42"),
                "the building's own line must reach the panel unchanged: " + lines);
    }

    /**
     * One over-long fact is wrapped across rows and capped, rather than drawn off the edge of the
     * panel or allowed to push everything else off it — a raw HTTP response body is the real case.
     * The cap's arithmetic is what this pins: the truncating branch indexes into the last row, and
     * an off-by-one there is a {@link StringIndexOutOfBoundsException} in the middle of drawing.
     */
    @Test
    void oneVeryLongFactIsWrappedAcrossRowsAndCappedWithAnEllipsis() {
        World world = new World(4, 4);
        SelfDescribing building = new SelfDescribing(List.of("x".repeat(5000)));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);
        List<String> wrapped = lines.subList(2, lines.size()); // past the shared label/status header

        assertEquals(6, wrapped.size(), "an unbounded body must be capped, not drawn in full: " + wrapped.size());
        assertTrue(wrapped.getLast().endsWith("..."), "the cut must be visible: " + wrapped.getLast());
        assertTrue(wrapped.stream().allMatch(line -> line.length() <= 56),
                "no row may exceed the panel's own width");
    }

    /** A building whose only interesting property is that this test file — and the panel — know nothing about its type. */
    private record SelfDescribing(List<String> details) implements Building, InspectableBuilding {

        @Override
        public List<String> inspectionDetails(TickContext world, int x, int y) {
            return details;
        }

        @Override
        public Appearance appearance() {
            return Appearance.of(VanillaSprites.CHEST);
        }


        @Override
        public Object state() {
            return details;
        }

        /**
         * A REGISTERED id, even though this fixture's Java class is unknown to everything. The
         * panel reads the prototype to title itself, and every building that can actually exist
         * came from the factory and therefore has one — a fixture claiming an unregistered id would
         * be testing a state the game cannot reach.
         */
        @Override
        public ContentId prototypeId() {
            return VanillaBuildings.idFor(BuildingType.CHEST);
        }
    }

    @Test
    void hitTestRecipeFindsTheRowUnderThePointAndMissesEverythingAboveTheRecipeSection() {
        int screenW = 1280;
        int screenH = 800;
        Recipe iron = RECIPES.find(BuildingType.FURNACE, VanillaItems.IRON_ORE).orElseThrow();
        Recipe bronze = RECIPES.find(BuildingType.FURNACE, VanillaItems.BRONZE_ORE).orElseThrow();
        List<Recipe> recipes = List.of(iron, bronze);
        int totalLineCount = 5; // 3 header lines (label/status/recipe summary) + 2 recipe rows
        int recipeSectionStart = totalLineCount - recipes.size();

        float panelX = InspectionPanelLayout.panelX(screenW);
        float panelH = InspectionPanelLayout.panelHeight(totalLineCount);
        float panelY = InspectionPanelLayout.panelY(screenH, totalLineCount);
        // A point inside the SECOND recipe row's band (index recipeSectionStart + 1).
        float rowHudY = panelY + panelH - (recipeSectionStart + 1) * InspectionPanelLayout.LINE_HEIGHT - 5f;
        float rowScreenX = panelX + 10f;
        float rowScreenY = screenH - rowHudY;

        assertEquals(Optional.of(bronze),
                InspectionPanelLayout.hitTestRecipe(rowScreenX, rowScreenY, screenW, screenH, totalLineCount, recipes));

        // A point in one of the header lines (row 0), above the recipe section entirely.
        float headerHudY = panelY + panelH - 5f;
        assertEquals(Optional.empty(),
                InspectionPanelLayout.hitTestRecipe(rowScreenX, screenH - headerHudY, screenW, screenH, totalLineCount, recipes));

        // No recipes at all (a Chest, say) — never a hit, regardless of where the click lands.
        assertEquals(Optional.empty(),
                InspectionPanelLayout.hitTestRecipe(rowScreenX, rowScreenY, screenW, screenH, totalLineCount, List.of()));
    }

    @Test
    void isOverPanelIsTrueInsideAndFalseOutsideAndWhenNothingIsOpen() {
        int screenW = 1280;
        int screenH = 800;
        int lineCount = 4;
        float panelX = InspectionPanelLayout.panelX(screenW);
        float panelH = InspectionPanelLayout.panelHeight(lineCount);
        float panelY = InspectionPanelLayout.panelY(screenH, lineCount);
        float midHudY = panelY + panelH / 2f;
        float insideScreenY = screenH - midHudY;

        assertTrue(InspectionPanelLayout.isOverPanel(panelX + 10, insideScreenY, screenW, screenH, lineCount));
        assertFalse(InspectionPanelLayout.isOverPanel(panelX - 10, insideScreenY, screenW, screenH, lineCount), "left of the panel entirely");
        assertFalse(InspectionPanelLayout.isOverPanel(panelX + 10, insideScreenY, screenW, screenH, 0), "no panel open right now");
    }
}
