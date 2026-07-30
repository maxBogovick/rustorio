package com.rustorio.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (S-03, DEV_TASKS.md) {@link RecipeBook}'s own constructor only rejects a LOCAL collision (two
 * recipes of the same {@code BuildingType} sharing the exact same ingredients) — nothing checks
 * the GRAPH those recipes form: whether it has a circular dependency, whether every item traces
 * back to raw ore, and whether every {@link ItemType#researchGrade()} item is actually reachable
 * that way, not just declared "researchable" on {@code ItemType} with no recipe chain that can ever
 * produce one.
 *
 * <p>Each check has both a positive case (the real, shipped {@link RecipeBook#standard()} passes)
 * and a negative one (a hand-built, deliberately broken book proves the detector actually catches
 * the thing it claims to) — the card's own acceptance criterion asks for exactly this: the test
 * must fail on an artificially introduced cycle or an unreachable item, checked by construction
 * here rather than by a one-off manual run that leaves no trace afterward.
 */
class RecipeBookGraphTest {

    /**
     * What {@code Miner}/{@code OreLayout} actually mine — the true starting points for
     * reachability, not "whatever item happens to have no recipe in a given book." The first
     * version of this test used the latter and it was wrong: a deliberately gapped book missing a
     * recipe for {@code ENGINE} made {@code ENGINE} look like valid ore instead of unreachable,
     * because nothing in THAT book produced it either. Caught by the negative test below actually
     * failing during development, not by inspection.
     */
    // COAL (D-05, DEV_TASKS.md) joined the roster: mined like ore, not crafted by any recipe —
    // same "true root" status as IRON_ORE/BRONZE_ORE, not just "happens to have no recipe."
    private static final Set<ItemType> BASE_ORE = new TreeSet<>(Set.of(VanillaItems.IRON_ORE, VanillaItems.BRONZE_ORE, VanillaItems.COAL));

    @Test
    void standardBookHasNoCycles() {
        assertFalse(hasCycle(RecipeBook.standard()), "RecipeBook.standard() must not have a circular dependency");
    }

    /** Proves the detector isn't vacuously agreeing: IRON_ORE needs GEAR, GEAR needs IRON_ORE. */
    @Test
    void detectsAnArtificiallyIntroducedCycle() {
        RecipeBook cyclic = new RecipeBook(List.of(
                new Recipe(VanillaItems.IRON_ORE, VanillaItems.GEAR, 1, BuildingType.FURNACE),
                new Recipe(VanillaItems.GEAR, VanillaItems.IRON_ORE, 1, BuildingType.PRESS)));

        assertTrue(hasCycle(cyclic), "the detector must catch IRON_ORE -> GEAR -> IRON_ORE");
    }

    @Test
    void everyItemInStandardBookIsReachableFromRawOre() {
        Set<ItemType> reachable = reachableItems(RecipeBook.standard());

        for (ItemType item : VanillaItems.frozen().iterate()) {
            assertTrue(reachable.contains(item), item + " is not reachable from any base ore through RecipeBook's recipes");
        }
    }

    /** Proves reachability actually excludes something: CHASSIS needs ENGINE, but nothing here produces ENGINE. */
    @Test
    void detectsAnItemWithNoPathBackToOre() {
        RecipeBook gapped = new RecipeBook(List.of(
                new Recipe(VanillaItems.IRON_ORE, VanillaItems.IRON_PLATE, 1, BuildingType.FURNACE),
                new Recipe(VanillaItems.ENGINE, VanillaItems.GEAR, VanillaItems.CHASSIS, 1, BuildingType.PRESS)));

        Set<ItemType> reachable = reachableItems(gapped);

        assertFalse(reachable.contains(VanillaItems.CHASSIS), "CHASSIS needs ENGINE and GEAR, and nothing in this book produces either");
    }

    @Test
    void everyResearchGradeItemInStandardBookIsActuallyProducible() {
        Set<ItemType> reachable = reachableItems(RecipeBook.standard());

        for (ItemType item : VanillaItems.frozen().iterate()) {
            if (item.researchGrade()) {
                assertTrue(reachable.contains(item),
                        item + " is marked research-grade but nothing in RecipeBook can actually produce one");
            }
        }
    }

    // ---- shared graph helpers ----

    /** Every recipe's output mapped to the full list of items it needs — merged, in case more than one recipe shares an output. */
    private static Map<ItemType, List<ItemType>> inputsByOutput(RecipeBook book) {
        Map<ItemType, List<ItemType>> inputs = new TreeMap<>();
        for (Recipe recipe : book.all()) {
            List<ItemType> recipeInputs = recipe.hasSecondInput()
                    ? List.of(recipe.input(), recipe.input2())
                    : List.of(recipe.input());
            inputs.merge(recipe.output(), recipeInputs, (existing, added) -> {
                List<ItemType> combined = new ArrayList<>(existing);
                combined.addAll(added);
                return combined;
            });
        }
        return inputs;
    }

    /** Standard DFS cycle detection (visited + on-stack) over the "needs" graph: output depends on its recipe's input(s). */
    private static boolean hasCycle(RecipeBook book) {
        Map<ItemType, List<ItemType>> inputsByOutput = inputsByOutput(book);
        Set<ItemType> visited = new TreeSet<>();
        Set<ItemType> onStack = new TreeSet<>();
        for (ItemType item : VanillaItems.frozen().iterate()) {
            if (!visited.contains(item) && hasCycleFrom(item, inputsByOutput, visited, onStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycleFrom(ItemType item, Map<ItemType, List<ItemType>> inputsByOutput, Set<ItemType> visited, Set<ItemType> onStack) {
        visited.add(item);
        onStack.add(item);
        for (ItemType input : inputsByOutput.getOrDefault(item, List.of())) {
            if (onStack.contains(input)) {
                return true; // back edge — input is an ancestor of item on the current path
            }
            if (!visited.contains(input) && hasCycleFrom(input, inputsByOutput, visited, onStack)) {
                return true;
            }
        }
        onStack.remove(item);
        return false;
    }

    /**
     * Fixed-point closure: an item is reachable once it's {@link #BASE_ORE} itself or every one of
     * its recipe's inputs is already reachable — an item with NO recipe that also isn't base ore
     * stays unreachable forever, it doesn't get a free pass. Loops until a full pass adds nothing
     * new, which terminates because the vanilla item registry is finite and each pass either grows
     * {@code reachable} or ends the loop.
     */
    private static Set<ItemType> reachableItems(RecipeBook book) {
        Map<ItemType, List<ItemType>> inputsByOutput = inputsByOutput(book);
        Set<ItemType> reachable = new TreeSet<>(BASE_ORE);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ItemType item : VanillaItems.frozen().iterate()) {
                if (reachable.contains(item)) {
                    continue;
                }
                List<ItemType> inputs = inputsByOutput.get(item);
                if (inputs != null && reachable.containsAll(inputs)) {
                    reachable.add(item);
                    changed = true;
                }
            }
        }
        return reachable;
    }
}
