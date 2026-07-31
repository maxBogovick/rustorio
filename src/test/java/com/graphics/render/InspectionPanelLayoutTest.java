package com.graphics.render;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
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
