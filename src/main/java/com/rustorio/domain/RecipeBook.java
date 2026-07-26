package com.rustorio.domain;

import java.util.List;
import java.util.Optional;

/**
 * Registry of every known {@link Recipe} — the lookup furnaces and presses search through to
 * decide what they've just been handed. Separating "the recipes that exist" (this class) from
 * "what a recipe is" ({@link Recipe}) lets {@code Furnace}/{@code Lab} depend on an injected
 * {@code RecipeBook} instead of static state, so an alternative rule set (a mod, a test fixture)
 * is a constructor argument, not a change to production code.
 *
 * <p><b>Owner decision (P2-02, BUG_FIX_PROGRESS.md):</b> option (C) — an item feeding more than
 * one recipe of the same {@code kind} (a {@code GEAR} starts both {@code ENGINE} and {@code
 * CHASSIS}) is a legitimate, supported ambiguity: see {@code Furnace.cycleRecipe} for how the
 * player resolves it. What the constructor still refuses is a strictly worse case — two recipes
 * of the same {@code kind} with the exact same ingredient set, which no amount of player choice
 * could tell apart.
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
        for (int i = 0; i < this.recipes.size(); i++) {
            Recipe a = this.recipes.get(i);
            for (int j = i + 1; j < this.recipes.size(); j++) {
                Recipe b = this.recipes.get(j);
                if (a.type() == b.type() && sameIngredients(a, b)) {
                    throw new IllegalArgumentException(
                            "Two " + a.type() + " recipes share the exact same ingredients: " + a + " and " + b);
                }
            }
        }
    }

    /** Same ingredient multiset, order ignored — {@code (GEAR, MECHANISM)} equals {@code (MECHANISM, GEAR)}. */
    private static boolean sameIngredients(Recipe a, Recipe b) {
        if (a.input2() == null || b.input2() == null) {
            return a.input2() == b.input2() && a.input() == b.input();
        }
        return (a.input() == b.input() && a.input2() == b.input2())
                || (a.input() == b.input2() && a.input2() == b.input());
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
     * Every recipe for {@code kind} that accepts {@code input} as an ingredient — plural, unlike
     * {@link #find}, because more than one can match (a {@code GEAR} starts both {@code ENGINE}
     * and {@code CHASSIS}). {@code Furnace.accept} uses the size of this list to tell "obvious"
     * from "needs the player to pick" — see P2-02 in BUG_FIX_PROGRESS.md.
     */
    public List<Recipe> findAll(BuildingType kind, Item input) {
        return recipes.stream()
                .filter(r -> r.type() == kind && (r.input() == input || r.input2() == input))
                .toList();
    }

    /** Every recipe {@code kind} can run — the candidates {@code Furnace.cycleRecipe} cycles through. */
    public List<Recipe> forKind(BuildingType kind) {
        return recipes.stream().filter(r -> r.type() == kind).toList();
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
