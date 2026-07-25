package com.rustorio.domain;

import java.util.List;
import java.util.Optional;

/**
 * Registry of every known {@link Recipe} — the lookup furnaces and presses search through to
 * decide what they've just been handed. Separating "the recipes that exist" (this class) from
 * "what a recipe is" ({@link Recipe}) lets {@code Furnace}/{@code Lab} depend on an injected
 * {@code RecipeBook} instead of static state, so an alternative rule set (a mod, a test fixture)
 * is a constructor argument, not a change to production code.
 */
public final class RecipeBook {

    private static final Recipe IRON = new Recipe(Item.IRON_ORE, Item.IRON_PLATE, 5, BuildingType.FURNACE);
    private static final Recipe GEAR = new Recipe(Item.IRON_PLATE, Item.GEAR, 8, BuildingType.PRESS);
    private static final Recipe BRONZE = new Recipe(Item.BRONZE_ORE, Item.BRONZE_PLATE, 5, BuildingType.FURNACE);
    private static final Recipe MECHANISM = new Recipe(Item.BRONZE_PLATE, Item.MECHANISM, 8, BuildingType.PRESS);
    private static final Recipe ENGINE =
            new Recipe(Item.GEAR, Item.MECHANISM, Item.ENGINE, 12, BuildingType.PRESS);
    private static final Recipe CHASSIS =
            new Recipe(Item.ENGINE, Item.GEAR, Item.CHASSIS, 15, BuildingType.PRESS);
    private static final Recipe ALLOY =
            new Recipe(Item.IRON_PLATE, Item.BRONZE_PLATE, Item.ALLOY_PLATE, 10, BuildingType.FURNACE);
    private static final Recipe ALLOY_GEAR =
            new Recipe(Item.ALLOY_PLATE, Item.ALLOY_GEAR, 10, BuildingType.PRESS);

    private static final RecipeBook STANDARD = new RecipeBook(
            List.of(IRON, GEAR, BRONZE, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR));

    private final List<Recipe> recipes;

    public RecipeBook(List<Recipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    /** The game's built-in recipe set — what every furnace/press is given unless told otherwise. */
    public static RecipeBook standard() {
        return STANDARD;
    }

    public List<Recipe> all() {
        return recipes;
    }

    /** Recipe for {@code kind} that accepts {@code input} as its first or second ingredient. */
    public Optional<Recipe> find(BuildingType kind, Item input) {
        return recipes.stream()
                .filter(r -> r.type() == kind && (r.input() == input || r.input2() == input))
                .findFirst();
    }

    /**
     * Recipe for {@code kind} identified by its OUTPUT rather than input — needed when reloading a
     * furnace that already committed to a recipe: a saved dual-input recipe's input is ambiguous
     * (which of the two arrived first?), but within one {@code kind} the output is always unique.
     */
    public Optional<Recipe> findByOutput(BuildingType kind, Item output) {
        return recipes.stream().filter(r -> r.type() == kind && r.output() == output).findFirst();
    }
}
