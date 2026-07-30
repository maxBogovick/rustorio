package com.rustorio.domain;

import java.util.List;

/**
 * A furnace/press recipe: what goes in, what comes out, how long it takes, and which {@link
 * BuildingType} role runs it. Pure data — see {@link RecipeBook} for the registry that furnaces
 * search through (Open/Closed principle: a new recipe is a new constant there, never a change to
 * {@code Furnace} itself).
 *
 * <p>{@code ingredients} — one or more, order-significant only for display (recipe book text) and
 * {@code Furnace}'s own tie-break when two ingredients happen to be the identical item (see that
 * class's {@code accept}). No per-ingredient quantity: every vanilla recipe today takes exactly
 * one unit of each of its inputs, so a count field nothing constructs would be exactly the
 * "abstraction for a future that isn't this card's job" the project's own design checklist warns
 * against — a real need for {@code 2x IRON_PLATE} can add one later, as a genuine requirement.
 */
public record Recipe(List<ItemType> ingredients, ItemType output, int time, BuildingType type) {

    public Recipe {
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one ingredient (output: " + output + ")");
        }
        ingredients = List.copyOf(ingredients);
    }

    /** Single-ingredient recipe — most recipes need only one input. */
    public Recipe(ItemType input, ItemType output, int time, BuildingType type) {
        this(List.of(input), output, time, type);
    }

    /** Two-ingredient recipe — e.g. {@code ENGINE} (a {@code GEAR} and a {@code MECHANISM}). */
    public Recipe(ItemType input, ItemType input2, ItemType output, int time, BuildingType type) {
        this(List.of(input, input2), output, time, type);
    }
}
