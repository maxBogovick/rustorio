package com.rustorio.api.content.model;

import com.rustorio.api.content.ContentId;
import java.util.List;

/**
 * A furnace/press recipe: what goes in, what comes out, how long it takes, and which "kind" runs
 * it — the {@link ContentId} a {@code Furnace}-archetype building's own governing pool is keyed
 * by ({@code BuildingPrototype#recipeKind()}), NOT necessarily one of the 12 {@code BuildingType}
 * constants: a JSON-defined custom archetype names its own, private pool (defaults to its own
 * prototype id) so its recipes never collide or go ambiguous against the vanilla FURNACE/PRESS/
 * ASSEMBLER pools, or another mod's. Pure data — see {@link com.rustorio.domain.RecipeBook} for
 * the registry that furnaces search through (Open/Closed principle: a new recipe is a new constant
 * there, never a change to {@code Furnace} itself).
 *
 * <p>{@code ingredients} — one or more, order-significant only for display (recipe book text) and
 * {@code Furnace}'s own tie-break when two ingredients happen to be the identical item (see that
 * class's {@code accept}). No per-ingredient quantity: every vanilla recipe today takes exactly
 * one unit of each of its inputs, so a count field nothing constructs would be exactly the
 * "abstraction for a future that isn't this card's job" the project's own design checklist warns
 * against — a real need for {@code 2x IRON_PLATE} can add one later, as a genuine requirement.
 *
 * <p>Convenience overloads take a bare {@link ContentId} for the kind — not {@code BuildingType} —
 * so this catalog model stays free of a domain dependency. Call sites that have a {@code
 * BuildingType} pass {@code type.contentId()}.
 */
public record Recipe(ContentId id, List<ItemType> ingredients, ItemType output, int time, ContentId type) {

    public Recipe {
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one ingredient (output: " + output + ")");
        }
        ingredients = List.copyOf(ingredients);
    }

    /** Single-ingredient recipe in one of the 12 vanilla kinds' own SHARED pool — most vanilla recipes need only one input. */
    public Recipe(ContentId id, ItemType input, ItemType output, int time, ContentId type) {
        this(id, List.of(input), output, time, type);
    }

    /** Two-ingredient recipe in one of the 12 vanilla kinds' own SHARED pool — e.g. {@code ENGINE} (a {@code GEAR} and a {@code MECHANISM}). */
    public Recipe(ContentId id, ItemType input, ItemType input2, ItemType output, int time, ContentId type) {
        this(id, List.of(input, input2), output, time, type);
    }
}
