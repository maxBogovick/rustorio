package com.rustorio.domain.building;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Печь + {@link ProcessTimer}: срок готовности порции и его тех-модификация FAST_SMELTING. Since
 * D-05 (DEV_TASKS.md), {@code FURNACE} kind additionally needs {@link ItemType#COAL} on hand to even
 * start a batch (§2.3 of the design audit) — every FURNACE-kind test here now feeds it one unit;
 * {@code PRESS}-kind tests are untouched (mechanical stamping, no fuel concept — see {@link
 * Furnace}'s own D-05 javadoc note for why the split is deliberate).
 */
class FurnaceTest {

    private static final RecipeBook RECIPES = RecipeBook.standard();
    private static final int IRON_TIME = RECIPES.find(BuildingType.FURNACE, VanillaItems.IRON_ORE).orElseThrow().time();
    private static final int CHASSIS_TIME = RECIPES.find(BuildingType.PRESS, VanillaItems.ENGINE).orElseThrow().time();
    private static final int ENGINE_TIME = RECIPES.find(BuildingType.PRESS, VanillaItems.MECHANISM).orElseThrow().time();
    private static final int ALLOY_TIME = RECIPES.find(BuildingType.FURNACE, VanillaItems.IRON_PLATE).orElseThrow().time();
    private static final int ALLOY_GEAR_TIME =
            RECIPES.find(BuildingType.PRESS, VanillaItems.ALLOY_PLATE).orElseThrow().time();
    private static final int GEAR_TIME = RECIPES.findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();

    @Test
    void smeltsIronOreIntoPlateAfterRecipeTime() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest); // держим ссылку на ящик напрямую, минуя place*

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        assertTrue(furnace.accept(world, VanillaItems.COAL), "FURNACE needs fuel too now (D-05, DEV_TASKS.md)");

        for (int i = 0; i < IRON_TIME - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(0, chest.count(), "не должно быть готово раньше срока рецепта");
        }
        furnace.tick(world, 0, 0); // последний тик — порция готова и сразу уходит соседу
        assertEquals(1, chest.count());
    }

    @Test
    void fastSmeltingHalvesTimeStartingFromTheFirstBatch() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);
        // Открываем FAST_SMELTING ДО того, как печь впервые подстроится под рецепт — именно этот
        // момент раньше терял тех-эффект (см. javadoc Furnace.accept про ProcessTimer). Разблокировка
        // теперь явное действие игрока (P-02, DEV_TASKS.md), не автоматическая, и FAST_SMELTING
        // требует уже открытого FAST_MINING.
        world.addResearchPoints(Tech.FAST_MINING.cost());
        assertTrue(world.tryUnlockTech(Tech.FAST_MINING));
        world.addResearchPoints(Tech.FAST_SMELTING.cost());
        assertTrue(world.tryUnlockTech(Tech.FAST_SMELTING));

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        assertTrue(furnace.accept(world, VanillaItems.COAL), "FURNACE needs fuel too now (D-05, DEV_TASKS.md)");

        int halvedTime = Math.max(1, IRON_TIME / 2);
        for (int i = 0; i < halvedTime - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        furnace.tick(world, 0, 0);
        assertEquals(1, chest.count(), "первая же порция обязана учитывать уже открытую технологию");
    }

    @Test
    void pressBuildsChassisFromEngineAndGear() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        // ENGINE — первый вход рецепта CHASSIS и ни для какого другого рецепта пресса входом не
        // служит, поэтому им и стоит кормить первым.
        assertTrue(press.accept(world, VanillaItems.ENGINE));
        assertTrue(press.accept(world, VanillaItems.GEAR));

        for (int i = 0; i < CHASSIS_TIME - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count(), "не должно быть готово раньше срока рецепта");
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }

    @Test
    void furnaceAlloysIronAndBronzePlatesTogether() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        // ALLOY — первый рецепт ПЕЧИ (не пресса) с двумя входами; порядок подачи не важен.
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.BRONZE_PLATE));
        assertTrue(furnace.accept(world, VanillaItems.IRON_PLATE));
        assertTrue(furnace.accept(world, VanillaItems.COAL), "FURNACE needs fuel too now (D-05, DEV_TASKS.md)");

        for (int i = 0; i < ALLOY_TIME - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        furnace.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }

    /**
     * (D-05, DEV_TASKS.md) The card's own acceptance criterion, verbatim: "Печь не производит
     * продукцию без подачи угля." Ore alone, however long you wait, must never finish a batch;
     * the moment coal arrives, the SAME already-buffered ore batch completes without needing to
     * be re-fed.
     */
    @Test
    void furnaceNeverFinishesABatchWithoutFuel() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));

        for (int i = 0; i < IRON_TIME * 3; i++) { // far past the recipe's own time, still no fuel
            furnace.tick(world, 0, 0);
        }
        assertEquals(0, chest.count(), "no coal was ever supplied — nothing should have finished, however long it waited");

        assertTrue(furnace.accept(world, VanillaItems.COAL));
        for (int i = 0; i < IRON_TIME; i++) {
            furnace.tick(world, 0, 0);
        }
        assertEquals(1, chest.count(), "the ore that was already buffered must complete once fuel finally arrives");
    }

    @Test
    void plainOreSmeltingIsUnaffectedByTheNewTwoInputAlloyRecipe() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE)); // тот же тип FURNACE, что и у ALLOY
        assertTrue(furnace.accept(world, VanillaItems.COAL), "FURNACE needs fuel too now (D-05, DEV_TASKS.md)");

        for (int i = 0; i < IRON_TIME; i++) {
            furnace.tick(world, 0, 0);
        }
        assertEquals(1, chest.count(), "IRON_ORE по-прежнему однозначно ведёт к IRON, а не к сплаву");
    }

    @Test
    void pressFedGearFirstDoesNotDeadlockForever() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);

        // GEAR is ambiguous on its own: it's ENGINE's first ingredient AND CHASSIS's second.
        // Guessing (the old behavior) could commit to a recipe whose other ingredient never
        // arrives — refusing instead means the item just doesn't move, not "gone forever".
        assertFalse(press.accept(world, VanillaItems.GEAR), "an ambiguous item must not be silently guessed at");

        // Player disambiguates: cycle forward to the ENGINE recipe (GEAR + MECHANISM -> ENGINE).
        Optional<ItemType> selected = Optional.empty();
        for (int i = 0; i < 3; i++) {
            selected = press.cycleRecipe();
        }
        assertEquals(Optional.of(VanillaItems.ENGINE), selected, "third cycle step must land on ENGINE");

        assertTrue(press.accept(world, VanillaItems.GEAR), "now that ENGINE is selected, GEAR is unambiguous");
        assertTrue(press.accept(world, VanillaItems.MECHANISM));

        for (int i = 0; i < ENGINE_TIME - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }

    /**
     * A recipe whose two ingredients are the SAME item must still be feedable (N12,
     * NEW_BUGS_PROGRESS.md). {@code accept} matched the first buffer and returned immediately, so
     * every unit piled into {@code bufferA} while {@code bufferB} stayed empty — and {@code tick}
     * refuses to start a batch without the second ingredient, jamming the press forever on a full
     * input buffer. Nothing in {@link RecipeBook} forbids such a recipe (its constructor only
     * rejects two recipes of one kind sharing an ingredient SET), so an alternative rule set — the
     * whole point of {@code RecipeBook} being injected — could always define one.
     */
    @Test
    void pressWithARecipeThatTakesTwoOfTheSameItemFillsBothBuffers() {
        RecipeBook doubleInput = new RecipeBook(java.util.List.of(
                new Recipe(VanillaItems.IRON_PLATE, VanillaItems.IRON_PLATE, VanillaItems.ALLOY_PLATE, 3, BuildingType.PRESS)));
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, doubleInput);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));

        for (int i = 0; i < 3 - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        press.tick(world, 0, 0);

        assertEquals(1, chest.count(), "two units of the one ingredient are exactly what the recipe asks for");
    }

    /**
     * A cooldown of 0 in a save means "no countdown recorded", never "this batch is done" (N10,
     * NEW_BUGS_PROGRESS.md). {@code ProcessTimer.tick} decrements FIRST, so a timer restored at 0
     * goes to -1, fails the {@code > 0} test and reports the batch finished on the very first tick
     * — a full recipe's worth of work for free. A live furnace never persists a 0 (the countdown is
     * always reset to at least 1), so this only arrives from a hand-edited or corrupted save; it
     * still must not be rewarded.
     */
    @Test
    void aRestoredFurnaceWithNoRecordedCooldownStartsAFullBatchInsteadOfFinishingInstantly() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        BuildingMemento.FurnaceState state = new BuildingMemento.FurnaceState(
                BuildingType.PRESS, Direction.RIGHT, 1, 0, 0, VanillaItems.GEAR, null, 0, null);
        Furnace press = new Furnace(state, RECIPES);

        press.tick(world, 0, 0);
        assertEquals(0, chest.count(), "one tick must not finish a whole GEAR batch");

        for (int i = 0; i < GEAR_TIME - 2; i++) {
            press.tick(world, 0, 0);
        }
        assertEquals(0, chest.count(), "still cooking");
        press.tick(world, 0, 0);
        assertEquals(1, chest.count(), "and done after the recipe's full time");
    }

    @Test
    void pressTurnsAlloyPlateIntoAlloyGear() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        assertTrue(press.accept(world, VanillaItems.ALLOY_PLATE));

        for (int i = 0; i < ALLOY_GEAR_TIME - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }

    /** (F-01, DEV_TASKS.md) Every reason a furnace can be blocked must surface as its own distinct status. */
    @Test
    void furnaceStatusReflectsWhyItIsBlocked() {
        World world = new World(4, 4);
        // No forward neighbor at all — deliberately, so a finished batch has nowhere to go
        // (exercises OUTPUT_FULL below without needing a full chest).

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        furnace.tick(world, 0, 0);
        assertEquals(BuildingStatus.NO_INPUT, furnace.appearance().status(),
                "nothing buffered yet at all");

        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        furnace.tick(world, 0, 0);
        assertEquals(BuildingStatus.NO_FUEL, furnace.appearance().status(),
                "ore is buffered, but a FURNACE still needs coal to start cooking (D-05, DEV_TASKS.md)");

        assertTrue(furnace.accept(world, VanillaItems.COAL));
        for (int i = 0; i < IRON_TIME - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(BuildingStatus.WORKING, furnace.appearance().status(),
                    "actively cooking, just not done yet");
        }
        furnace.tick(world, 0, 0); // batch completes, but (0,0)'s forward neighbor doesn't exist
        assertEquals(BuildingStatus.OUTPUT_FULL, furnace.appearance().status(),
                "a finished batch with nowhere to go is a real problem, not silent success");
    }

    /**
     * (Code review finding, CODE_REVIEW_2026-07-28.md) {@code rotatedClockwise} rebuilds through
     * {@code memento()}, whose {@code FurnaceState} doesn't carry status (it's ephemeral) — without
     * explicitly carrying it over, a rotation would silently reset an actually-blocked furnace back
     * to {@code WORKING} for one tick, same class of bug as {@code Chest#rotatedClockwise}.
     */
    @Test
    void rotatingAnOutputFullFurnaceKeepsItsStatus() {
        World world = new World(4, 4);
        // No forward neighbor at all — a finished batch has nowhere to go (same setup as
        // furnaceStatusReflectsWhyItIsBlocked below).
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        assertTrue(furnace.accept(world, VanillaItems.COAL));
        for (int i = 0; i < IRON_TIME; i++) {
            furnace.tick(world, 0, 0);
        }
        assertEquals(BuildingStatus.OUTPUT_FULL, furnace.appearance().status(), "sanity check before rotating");

        Furnace rotated = (Furnace) furnace.rotatedClockwise().orElseThrow();

        assertEquals(BuildingStatus.OUTPUT_FULL, rotated.appearance().status(),
                "rotating must not silently reset an actually-blocked furnace back to WORKING");
    }

    /** (F-01, DEV_TASKS.md) A press with an ambiguous input still buffered (never committed to a recipe) also reads as NO_INPUT. */
    @Test
    void furnaceWithSecondInputMissingReportsNoInput() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.BRONZE_PLATE)); // ALLOY's first input; second (IRON_PLATE) never arrives
        assertTrue(furnace.accept(world, VanillaItems.COAL));

        furnace.tick(world, 0, 0);
        assertEquals(BuildingStatus.NO_INPUT, furnace.appearance().status(),
                "has fuel and one ingredient, still waiting on the recipe's second input");
    }

    /** (F-03, DEV_TASKS.md) "выбранный рецепт печи... виден на экране" — not just logged on cycle. */
    @Test
    void appearanceShowsTheCommittedRecipeAsARecipeHint() {
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertNull(furnace.appearance().recipeHint(), "nothing committed yet — no hint to show");

        World world = new World(4, 4);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        assertEquals(VanillaItems.IRON_PLATE, furnace.appearance().recipeHint(),
                "IRON_ORE unambiguously commits to the IRON_ORE -> IRON_PLATE recipe");
    }

    /** (F-03, DEV_TASKS.md) The player's standing recipe choice also shows before anything is committed. */
    @Test
    void appearanceShowsTheStandingSelectionBeforeAnythingIsCommitted() {
        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        assertNull(press.appearance().recipeHint());

        for (int i = 0; i < 3; i++) { // same three cycles as pressFedGearFirstDoesNotDeadlockForever, lands on ENGINE
            press.cycleRecipe();
        }

        assertEquals(VanillaItems.ENGINE, press.appearance().recipeHint());
    }

    /**
     * (F-03, DEV_TASKS.md) {@code Furnace#selectedRecipe}'s own javadoc used to say flatly "Not
     * persisted" — this closes that gap: {@code BuildingMemento.FurnaceState} now carries it, so a
     * save/load (here: a full {@code BuildingFactory.restore} round trip, not just {@code
     * memento()} in isolation) must remember which recipe an otherwise-ambiguous item like GEAR
     * commits to.
     */
    @Test
    void selectedRecipeSurvivesAFactoryRestoreRoundTrip() {
        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        for (int i = 0; i < 3; i++) {
            press.cycleRecipe();
        }

        BuildingFactory factory = BuildingFactory.standard();
        Furnace restored = (Furnace) factory.restore(press.memento(), 0);

        World world = new World(4, 4);
        assertTrue(restored.accept(world, VanillaItems.GEAR),
                "restored furnace must still remember ENGINE was selected — GEAR alone is ambiguous otherwise");
    }

    /** (F-03, DEV_TASKS.md) Same forgetfulness risk as every other field rotation must carry — see SpeedModule's own javadoc note on the pattern. */
    @Test
    void selectedRecipeSurvivesRotation() {
        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        for (int i = 0; i < 3; i++) {
            press.cycleRecipe();
        }

        Furnace rotated = (Furnace) press.rotatedClockwise().orElseThrow();

        World world = new World(4, 4);
        assertTrue(rotated.accept(world, VanillaItems.GEAR),
                "rotating must not drop the player's selected recipe");
    }

    /**
     * (Live bug report) The inspection panel used to show only a recipe's OUTPUT ({@code
     * Appearance#recipeHint}) — these are the full-recipe accessors that fixed it: input(s) AND
     * output, plus the full menu of what a kind can make at all before anything is committed.
     */
    @Test
    void possibleRecipesListsEveryRecipeForThisKind() {
        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);

        assertEquals(RECIPES.forKind(BuildingType.PRESS), press.possibleRecipes());
        assertFalse(press.possibleRecipes().isEmpty());
    }

    @Test
    void activeRecipeIsEmptyUntilSomethingCommitsThenCarriesTheFullInputAndOutput() {
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.activeRecipe().isEmpty());

        World world = new World(4, 4);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));

        Recipe active = furnace.activeRecipe().orElseThrow();
        assertEquals(VanillaItems.IRON_ORE, active.input());
        assertEquals(VanillaItems.IRON_PLATE, active.output());
    }

    @Test
    void selectedRecipeChoiceReflectsCycleRecipeAndStepsAsideOnceSomethingCommits() {
        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        assertTrue(press.selectedRecipeChoice().isEmpty());

        for (int i = 0; i < 3; i++) { // same three cycles as pressFedGearFirstDoesNotDeadlockForever — lands on ENGINE
            press.cycleRecipe();
        }
        assertEquals(VanillaItems.ENGINE, press.selectedRecipeChoice().orElseThrow().output());

        World world = new World(4, 4);
        assertTrue(press.accept(world, VanillaItems.GEAR)); // commits — activeRecipe takes over from here
        assertTrue(press.activeRecipe().isPresent());
    }

    /**
     * (X-03, DEV_TASKS.md) {@code ASSEMBLER} reuses {@link Furnace} wholesale — its own recipe
     * (see {@code RecipeBook#CHASSIS_ASSEMBLED}) and its 2x2 footprint are the only differences
     * from {@code PRESS}. {@code CHASSIS_TIME} above is the SAME 15-tick recipe time, since the
     * assembled recipe was declared with identical ingredients/time on purpose.
     */
    @Test
    void assemblerCraftsChassisFromEngineAndGear() {
        World world = new World(6, 6);
        Chest chest = new Chest();
        // Anchored at (0,0) facing RIGHT, footprint 2x2 — its own outputX/outputY geometry
        // (see Furnace's javadoc) pushes output to (2, 0), NOT (1, 0), which is still this same
        // building's own top-right cell.
        world.restoreBuilding(2, 0, chest);

        Furnace assembler = new Furnace(BuildingType.ASSEMBLER, Direction.RIGHT, RECIPES);
        assertTrue(assembler.accept(world, VanillaItems.ENGINE));
        assertTrue(assembler.accept(world, VanillaItems.GEAR));

        for (int i = 0; i < CHASSIS_TIME - 1; i++) {
            assembler.tick(world, 0, 0);
            assertEquals(0, chest.count(), "must not finish before the recipe's own time");
        }
        assembler.tick(world, 0, 0);

        assertEquals(1, chest.amount(VanillaItems.CHASSIS));
    }

    /**
     * The bug {@code Furnace}'s private {@code outputX}/{@code outputY} exist to prevent: a naive
     * {@code x + direction.dx()} would push output to {@code (1, 0)}, still inside a 2x2 assembler
     * anchored at {@code (0, 0)} — this pins the corrected geometry down directly, for all four
     * facings, independent of the recipe test above.
     */
    @Test
    void assemblerPushesOutputPastItsOwnFootprintOnEveryFacing() {
        World world = new World(6, 6);
        Chest right = new Chest();
        Chest down = new Chest();
        Chest left = new Chest();
        Chest up = new Chest();
        world.restoreBuilding(4, 2, right); // RIGHT-facing assembler anchored (2,2): 2+2=4
        world.restoreBuilding(2, 4, down); // DOWN-facing: 2+2=4
        world.restoreBuilding(1, 2, left); // LEFT-facing: 2-1=1
        world.restoreBuilding(2, 1, up); // UP-facing: 2-1=1

        assertProducesInto(RECIPES, Direction.RIGHT, world, right);
        assertProducesInto(RECIPES, Direction.DOWN, world, down);
        assertProducesInto(RECIPES, Direction.LEFT, world, left);
        assertProducesInto(RECIPES, Direction.UP, world, up);
    }

    private static void assertProducesInto(RecipeBook recipes, Direction direction, World world, Chest expected) {
        Furnace assembler = new Furnace(BuildingType.ASSEMBLER, direction, recipes);
        assembler.accept(world, VanillaItems.ENGINE);
        assembler.accept(world, VanillaItems.GEAR);
        for (int i = 0; i < CHASSIS_TIME; i++) {
            assembler.tick(world, 2, 2);
        }
        assertEquals(1, expected.amount(VanillaItems.CHASSIS), "facing " + direction);
    }
}
