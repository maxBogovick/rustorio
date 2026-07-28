package com.rustorio.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    /**
     * (X-03, DEV_TASKS.md) Same ingredients/output/time as {@link #CHASSIS} — deliberately: this
     * doesn't give {@code ASSEMBLER} a new item to make, only a second, four-cell-footprint MACHINE
     * that can make an existing one, same as {@code FURNACE}/{@code PRESS} sharing raw stamping
     * duty for different item families. Purely additive, not a reassignment of {@link #CHASSIS}
     * itself: {@link RecipeBook}'s own constructor only rejects a collision within the SAME {@code
     * kind}, and {@code RecipeBookGraphTest}'s {@code inputsByOutput} explicitly merges recipes
     * that share an output, so a second CHASSIS producer changes neither reachability nor the
     * dependency graph's shape. {@link #depthOf} still reports {@link #CHASSIS}'s value for
     * research pricing (P-01) — it takes the FIRST recipe by declaration order for a given output,
     * and this one is declared after it.
     */
    private static final Recipe CHASSIS_ASSEMBLED =
            new Recipe(Item.ENGINE, Item.GEAR, Item.CHASSIS, 15, BuildingType.ASSEMBLER);

    private static final RecipeBook STANDARD = new RecipeBook(
            List.of(IRON, GEAR, BRONZE, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR, CHASSIS_ASSEMBLED));

    private final List<Recipe> recipes;
    /**
     * Each item's {@link #depthOf}, filled in the first time it's asked for (N16,
     * NEW_BUGS_PROGRESS.md). The only mutable state on an otherwise immutable class, and it holds
     * nothing but a pure function of {@link #recipes}, which never changes — so a stale or missing
     * entry is impossible by construction, and the worst a second caller can do is compute the same
     * number twice. See {@link #depthOf} for why this is lazy rather than precomputed.
     */
    private final Map<Item, Integer> depthCache = new EnumMap<>(Item.class);

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

    /**
     * How many ticks of machine time it takes to produce one {@code item}, counting its ENTIRE
     * production chain — its own recipe's {@link Recipe#time()} plus the same recursive cost of
     * every one of its inputs, all the way back to raw ore. {@code 0} for an item no recipe here
     * produces (raw ore — mined, not crafted, so it costs nothing to "make").
     *
     * <p>(P-01, DEV_TASKS.md; §2.4 of the design audit.) Exists so {@link
     * com.rustorio.domain.building.Lab} can price research points by what an item actually costs to
     * produce instead of crediting every research-grade item the same flat point regardless of
     * depth — the audit's own worked numbers ({@code GEAR}=13, {@code ALLOY_GEAR}=30, {@code
     * ENGINE}=38, {@code CHASSIS}=66) are exactly what this method returns for the standard book,
     * which is how its correctness was checked while writing it.
     *
     * <p>Computed once per item and remembered in {@link #depthCache} (N16, NEW_BUGS_PROGRESS.md —
     * owner decision). It used to re-walk the entire chain below an item, re-scanning {@link
     * #recipes} at every level, on every single call; the recipes can't change after construction,
     * so every one of those walks after the first produced the same answer the previous one already
     * had. Memoized lazily rather than precomputed in the constructor on purpose: {@code
     * RecipeBookGraphTest} (S-03) deliberately BUILDS a cyclic book to prove its cycle detector
     * isn't vacuous, and a cyclic book must stay constructible — this way an unanswerable question
     * only blows up if someone actually asks it, exactly as before.
     */
    public int depthOf(Item item) {
        Integer known = depthCache.get(item);
        if (known != null) {
            return known;
        }
        // First recipe by declaration order — a second recipe producing the same output
        // (ASSEMBLER's CHASSIS, X-03) must not change the depth this reports.
        Optional<Recipe> recipe = recipes.stream().filter(r -> r.output() == item).findFirst();
        int depth;
        if (recipe.isEmpty()) {
            depth = 0; // raw ore — mined, not crafted, so it costs nothing to "make"
        } else {
            Recipe r = recipe.get();
            Item input2 = r.input2(); // local, not r.input2() again below — NullAway can't see hasSecondInput()'s guarantee across a ternary
            depth = r.time() + depthOf(r.input()) + (input2 != null ? depthOf(input2) : 0);
        }
        depthCache.put(item, depth); // only ever reached for a finite chain — see the javadoc above
        return depth;
    }
}
