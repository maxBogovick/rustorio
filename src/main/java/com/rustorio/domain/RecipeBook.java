package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.HashMap;
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

    /**
     * A vanilla recipe's own id. Matches the file name of the same recipe under {@code
     * resources/mods/rustorio/content/recipes} — the two describe the same recipe and {@code
     * VanillaAsModParityTest} holds them to it, so a mod that adjusts "the vanilla iron recipe"
     * names one thing whichever path the game was loaded through.
     */
    private static ContentId recipeId(String path) {
        return new ContentId("rustorio", path);
    }

    private static final Recipe IRON =
            new Recipe(recipeId("iron_plate"), VanillaItems.IRON_ORE, VanillaItems.IRON_PLATE, 5, BuildingType.FURNACE);
    private static final Recipe GEAR =
            new Recipe(recipeId("gear"), VanillaItems.IRON_PLATE, VanillaItems.GEAR, 8, BuildingType.PRESS);
    private static final Recipe BRONZE =
            new Recipe(recipeId("bronze_plate"), VanillaItems.BRONZE_ORE, VanillaItems.BRONZE_PLATE, 5, BuildingType.FURNACE);
    private static final Recipe MECHANISM =
            new Recipe(recipeId("mechanism"), VanillaItems.BRONZE_PLATE, VanillaItems.MECHANISM, 8, BuildingType.PRESS);
    private static final Recipe ENGINE =
            new Recipe(recipeId("engine"), VanillaItems.GEAR, VanillaItems.MECHANISM, VanillaItems.ENGINE, 12, BuildingType.PRESS);
    private static final Recipe CHASSIS =
            new Recipe(recipeId("chassis_press"), VanillaItems.ENGINE, VanillaItems.GEAR, VanillaItems.CHASSIS, 15, BuildingType.PRESS);
    private static final Recipe ALLOY =
            new Recipe(recipeId("alloy_plate"), VanillaItems.IRON_PLATE, VanillaItems.BRONZE_PLATE, VanillaItems.ALLOY_PLATE, 10, BuildingType.FURNACE);
    private static final Recipe ALLOY_GEAR =
            new Recipe(recipeId("alloy_gear"), VanillaItems.ALLOY_PLATE, VanillaItems.ALLOY_GEAR, 10, BuildingType.PRESS);
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
            new Recipe(recipeId("chassis_assembler"), VanillaItems.ENGINE, VanillaItems.GEAR, VanillaItems.CHASSIS, 15,
                    BuildingType.ASSEMBLER);

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
    private final Map<ItemType, Integer> depthCache = new HashMap<>();

    public RecipeBook(List<Recipe> recipes) {
        this.recipes = List.copyOf(recipes);
        for (int i = 0; i < this.recipes.size(); i++) {
            Recipe a = this.recipes.get(i);
            for (int j = i + 1; j < this.recipes.size(); j++) {
                Recipe b = this.recipes.get(j);
                // .equals(), not == — a.type()/b.type() is a ContentId now (a plain record, not an
                // enum constant): two logically-identical ids built from separate call sites (e.g.
                // one from BuildingType.contentId(), one parsed fresh off JSON) are equal but NOT
                // the same reference, so == would silently miss a real collision.
                if (a.type().equals(b.type()) && sameIngredients(a, b)) {
                    throw new IllegalArgumentException(
                            "Two " + a.type() + " recipes share the exact same ingredients: " + a + " and " + b);
                }
            }
        }
    }

    /** Same ingredient multiset, order ignored — {@code (GEAR, MECHANISM)} equals {@code (MECHANISM, GEAR)}, for any number of ingredients. */
    private static boolean sameIngredients(Recipe a, Recipe b) {
        if (a.ingredients().size() != b.ingredients().size()) {
            return false;
        }
        // Sorting a copy of each (ItemType's own Comparable, by ContentId) turns "same multiset,
        // any order" into a plain list-equality check — the same trick used wherever this project
        // needs order-independent comparison of a small collection.
        List<ItemType> sortedA = a.ingredients().stream().sorted().toList();
        List<ItemType> sortedB = b.ingredients().stream().sorted().toList();
        return sortedA.equals(sortedB);
    }

    /** The game's built-in recipe set — what every furnace/press is given unless told otherwise. */
    public static RecipeBook standard() {
        return STANDARD;
    }

    public List<Recipe> all() {
        return recipes;
    }

    /** Recipe for {@code kind} that accepts {@code input} as its first or second ingredient. */
    public Optional<Recipe> find(ContentId kind, ItemType input) {
        return recipes.stream()
                .filter(r -> r.type().equals(kind) && r.ingredients().contains(input))
                .findFirst();
    }

    /** Convenience for one of the 12 vanilla kinds' own shared pool — see {@link #find(ContentId, ItemType)}. */
    public Optional<Recipe> find(BuildingType kind, ItemType input) {
        return find(kind.contentId(), input);
    }

    /**
     * Every recipe for {@code kind} that accepts {@code input} as an ingredient — plural, unlike
     * {@link #find}, because more than one can match (a {@code GEAR} starts both {@code ENGINE}
     * and {@code CHASSIS}). {@code Furnace.accept} uses the size of this list to tell "obvious"
     * from "needs the player to pick" — see P2-02 in BUG_FIX_PROGRESS.md.
     */
    public List<Recipe> findAll(ContentId kind, ItemType input) {
        return recipes.stream()
                .filter(r -> r.type().equals(kind) && r.ingredients().contains(input))
                .toList();
    }

    /** Convenience for one of the 12 vanilla kinds' own shared pool — see {@link #findAll(ContentId, ItemType)}. */
    public List<Recipe> findAll(BuildingType kind, ItemType input) {
        return findAll(kind.contentId(), input);
    }

    /** Every recipe {@code kind} can run — the candidates {@code Furnace.cycleRecipe} cycles through. */
    public List<Recipe> forKind(ContentId kind) {
        return recipes.stream().filter(r -> r.type().equals(kind)).toList();
    }

    /** Convenience for one of the 12 vanilla kinds' own shared pool — see {@link #forKind(ContentId)}. */
    public List<Recipe> forKind(BuildingType kind) {
        return forKind(kind.contentId());
    }

    /**
     * Recipe for {@code kind} identified by its OUTPUT rather than input — needed when reloading a
     * furnace that already committed to a recipe: a saved dual-input recipe's input is ambiguous
     * (which of the two arrived first?), but within one {@code kind} the output is always unique.
     */
    public Optional<Recipe> findByOutput(ContentId kind, ItemType output) {
        return recipes.stream().filter(r -> r.type().equals(kind) && r.output().equals(output)).findFirst();
    }

    /** Convenience for one of the 12 vanilla kinds' own shared pool — see {@link #findByOutput(ContentId, ItemType)}. */
    public Optional<Recipe> findByOutput(BuildingType kind, ItemType output) {
        return findByOutput(kind.contentId(), output);
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
    public int depthOf(ItemType item) {
        Integer known = depthCache.get(item);
        if (known != null) {
            return known;
        }
        // First recipe by declaration order — a second recipe producing the same output
        // (ASSEMBLER's CHASSIS, X-03) must not change the depth this reports.
        Optional<Recipe> recipe = recipes.stream().filter(r -> r.output().equals(item)).findFirst();
        int depth;
        if (recipe.isEmpty()) {
            depth = 0; // raw ore — mined, not crafted, so it costs nothing to "make"
        } else {
            Recipe r = recipe.get();
            depth = r.time();
            for (ItemType ingredient : r.ingredients()) {
                depth += depthOf(ingredient);
            }
        }
        depthCache.put(item, depth); // only ever reached for a finite chain — see the javadoc above
        return depth;
    }
}
