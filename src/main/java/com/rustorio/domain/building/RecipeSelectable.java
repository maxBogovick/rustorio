package com.rustorio.domain.building;

import com.rustorio.domain.Recipe;
import java.util.List;
import java.util.Optional;

/**
 * A building that picks which {@link Recipe} it runs from among several candidates for its own
 * kind — currently only {@link Furnace} (its FURNACE/PRESS/ASSEMBLER archetypes). A capability
 * interface, not a concrete-subtype check: the inspection panel's recipe picker ({@code
 * com.graphics.render.InspectionPanelLayout}, {@code com.graphics.input.InputHandler}) needs to
 * ask "can I show a recipe list for this building, and which entry is selected" without hardcoding
 * that {@link Furnace} is the only thing that currently answers yes — same reason {@link
 * TransportNode}/{@link SettlesEachTick} exist instead of an {@code instanceof}/{@code switch}
 * naming every concrete kind (see {@code ContentCouplingRatchetTest}, which tracks exactly that).
 */
public interface RecipeSelectable {

    /** Every recipe this building's kind can run at all — what a picker UI lists. */
    List<Recipe> possibleRecipes();

    /** The player's standing preference among {@link #possibleRecipes}, if any. */
    Optional<Recipe> selectedRecipeChoice();

    /** Jump the standing preference straight to {@code recipe} — must be one of {@link #possibleRecipes}. */
    void selectRecipe(Recipe recipe);
}
