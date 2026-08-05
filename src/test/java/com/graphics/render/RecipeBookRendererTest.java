package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.VanillaItems;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link RecipeBookRenderer#visibleRecipes} - pure list-slicing, no libGDX, same reason {@link
 * BuildMenuLayoutTest} exercises {@code BuildMenuLayout.visibleTiles} directly.
 *
 * <p>Live bug report: recipes are JSON content loaded through the same open registry as buildings
 * ({@code com.rustorio.mod}) - before this class had its own cap, a mod registering more than
 * roughly 28 recipes drew a panel taller than the default window with the bottom rows simply
 * running off-screen, no scrollbar, no indication anything was missing.
 */
class RecipeBookRendererTest {

    @Test
    void aShortListIsReturnedAsIs() {
        List<Recipe> few = List.of(
                new Recipe(testRecipeId(1), VanillaItems.IRON_ORE, VanillaItems.IRON_PLATE, 5, BuildingType.FURNACE),
                new Recipe(testRecipeId(2), VanillaItems.IRON_PLATE, VanillaItems.GEAR, 5, BuildingType.PRESS));

        assertSame(few, RecipeBookRenderer.visibleRecipes(few), "no truncation needed, returns the same list");
    }

    @Test
    void aListLongerThanTheCapIsTruncatedNotHidden() {
        List<Recipe> tooMany = java.util.stream.IntStream.range(0, RecipeBookRenderer.MAX_VISIBLE_RECIPES + 7)
                .mapToObj(i -> new Recipe(testRecipeId(3), VanillaItems.IRON_ORE, VanillaItems.IRON_PLATE, i + 1, BuildingType.FURNACE))
                .toList();

        List<Recipe> visible = RecipeBookRenderer.visibleRecipes(tooMany);

        assertEquals(RecipeBookRenderer.MAX_VISIBLE_RECIPES, visible.size());
        assertEquals(tooMany.subList(0, RecipeBookRenderer.MAX_VISIBLE_RECIPES), visible,
                "truncates from the front, doesn't drop/reorder arbitrarily");
    }

    @Test
    void exactlyAtTheCapIsNotTruncated() {
        List<Recipe> exact = java.util.stream.IntStream.range(0, RecipeBookRenderer.MAX_VISIBLE_RECIPES)
                .mapToObj(i -> new Recipe(testRecipeId(4), VanillaItems.IRON_ORE, VanillaItems.IRON_PLATE, i + 1, BuildingType.FURNACE))
                .toList();

        assertEquals(RecipeBookRenderer.MAX_VISIBLE_RECIPES, RecipeBookRenderer.visibleRecipes(exact).size());
    }

    /** A distinct id per fixture recipe — recipes are addressable content now, and a fixture still has to say which one it means. */
    private static ContentId testRecipeId(int index) {
        return new ContentId("test", "fixture_recipe_" + index);
    }
}
