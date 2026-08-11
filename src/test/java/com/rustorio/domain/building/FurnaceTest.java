package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.model.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.domain.world.World;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * (E5-05, owner decision) {@code speedLevel} must still yield exactly 2^N real ticks per world
     * tick — preserved from the old decorator's stacking multiplier, not weakened to a linear
     * (1+N). At {@code speedLevel} 3 that's 8 internal cycles, enough to clear IRON's 5-tick recipe
     * within a SINGLE call to {@link Furnace#tick}.
     */
    @Test
    void speedLevelThreeSmeltsABatchWithinASingleTickCall() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace furnace = (Furnace) new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES).withSpeedLevel(3);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        assertTrue(furnace.accept(world, VanillaItems.COAL));

        furnace.tick(world, 0, 0);

        assertEquals(1, chest.count(),
                "speedLevel 3 (2^3 = 8 cycles) must clear IRON's 5-tick recipe within one tick() call");
        assertEquals(3, furnace.speedLevel());
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
        world.addResearchPoints(costOf(VanillaTechs.FAST_MINING));
        assertTrue(world.tryUnlockTech(VanillaTechs.FAST_MINING));
        world.addResearchPoints(costOf(VanillaTechs.FAST_SMELTING));
        assertTrue(world.tryUnlockTech(VanillaTechs.FAST_SMELTING));

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
     * The click-a-row counterpart to {@link #pressFedGearFirstDoesNotDeadlockForever}'s
     * cycle-by-key test — {@link Furnace#selectRecipe} jumps straight to the recipe named,
     * skipping the cycling, for the inspection panel's recipe picker.
     */
    @Test
    void selectRecipeJumpsDirectlyToTheChosenCandidate() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        Recipe engineRecipe = RECIPES.findByOutput(BuildingType.PRESS, VanillaItems.ENGINE).orElseThrow();

        assertFalse(press.accept(world, VanillaItems.GEAR), "still ambiguous before any selection");

        press.selectRecipe(engineRecipe);
        assertEquals(Optional.of(engineRecipe), press.selectedRecipeChoice());

        assertTrue(press.accept(world, VanillaItems.GEAR), "now unambiguous — ENGINE was picked directly");
        assertTrue(press.accept(world, VanillaItems.MECHANISM));

        for (int i = 0; i < ENGINE_TIME; i++) {
            press.tick(world, 0, 0);
        }
        assertEquals(1, chest.count());
    }

    /** A recipe belonging to a DIFFERENT kind (or an entirely unrelated one) is never a valid choice — see {@link Furnace#selectRecipe}'s own javadoc. */
    @Test
    void selectRecipeRejectsARecipeFromAnotherKind() {
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        Recipe pressOnlyRecipe = RECIPES.findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> furnace.selectRecipe(pressOnlyRecipe));
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
                new Recipe(testRecipeId(1), VanillaItems.IRON_PLATE, VanillaItems.IRON_PLATE, VanillaItems.ALLOY_PLATE, 3, BuildingType.PRESS.contentId())));
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
     * The phase's other acceptance example, proven directly: a recipe with three DIFFERENT
     * ingredients (not just the vanilla book's usual one or two) commits, buffers each ingredient
     * in its own slot, and cooks correctly — nothing about {@link Recipe}/{@link Furnace} is
     * hardcoded to at most two inputs anymore.
     */
    @Test
    void aRecipeWithThreeIngredientsCommitsAndCooksCorrectly() {
        Recipe tripleInput = new Recipe(testRecipeId(2), 
                List.of(VanillaItems.IRON_PLATE, VanillaItems.BRONZE_PLATE, VanillaItems.GEAR),
                VanillaItems.CHASSIS, 6, BuildingType.PRESS.contentId());
        RecipeBook customBook = new RecipeBook(List.of(tripleInput));
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, customBook);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));
        assertTrue(press.accept(world, VanillaItems.BRONZE_PLATE));
        assertTrue(press.accept(world, VanillaItems.GEAR));

        for (int i = 0; i < 6; i++) {
            press.tick(world, 0, 0);
        }
        assertEquals(1, chest.amount(VanillaItems.CHASSIS), "all three ingredients present must be enough to finish the batch");
    }

    /** The other half of the same guarantee: two out of three ingredients must NOT be enough. */
    @Test
    void aRecipeWithThreeIngredientsRefusesToCookWithOnlyTwoDelivered() {
        Recipe tripleInput = new Recipe(testRecipeId(3), 
                List.of(VanillaItems.IRON_PLATE, VanillaItems.BRONZE_PLATE, VanillaItems.GEAR),
                VanillaItems.CHASSIS, 6, BuildingType.PRESS.contentId());
        RecipeBook customBook = new RecipeBook(List.of(tripleInput));
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, customBook);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));
        assertTrue(press.accept(world, VanillaItems.BRONZE_PLATE));

        for (int i = 0; i < 10; i++) {
            press.tick(world, 0, 0);
        }
        assertEquals(0, chest.count(), "the third ingredient (GEAR) never arrived — must not cook");
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

        FurnaceState state = new FurnaceState(
                Direction.RIGHT, List.of(1), 0, VanillaItems.GEAR, null, 0, null, 0);
        Furnace press = new Furnace(BuildingType.PRESS, state, RECIPES);

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
     * persisted" — this closes that gap: {@link FurnaceState} carries it, so a save/load (here: a
     * full {@code BuildingFactory.restore} round trip, not just {@code state()} in isolation) must
     * remember which recipe an otherwise-ambiguous item like GEAR commits to.
     */
    @Test
    void selectedRecipeSurvivesAFactoryRestoreRoundTrip() {
        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        for (int i = 0; i < 3; i++) {
            press.cycleRecipe();
        }

        BuildingFactory factory = BuildingFactory.standard();
        BuildingPrototype prototype = factory.prototype(press.prototypeId());
        Object encoded = prototype.encodeState(press.state());
        Furnace restored = (Furnace) factory.restore(press.prototypeId(), encoded);

        World world = new World(4, 4);
        assertTrue(restored.accept(world, VanillaItems.GEAR),
                "restored furnace must still remember ENGINE was selected — GEAR alone is ambiguous otherwise");
    }

    /** (F-03, DEV_TASKS.md) Same forgetfulness risk as every other field rotation must carry — see {@code rotatedClockwise}'s own javadoc note on the pattern. */
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
        assertEquals(List.of(VanillaItems.IRON_ORE), active.ingredients());
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

    /**
     * The phase's own acceptance example, proven directly (not through {@link BuildingFactory} —
     * see the owner's "narrow path" decision): a furnace built with a custom {@link
     * BuildingPrototype} (buffer 10, twice the vanilla speed) buffers more than the vanilla default
     * (5) and finishes a batch in roughly half the time — both numbers read from the prototype, not
     * a hardcoded constant.
     */
    @Test
    void aCustomPrototypeGivesTheFurnaceABiggerBufferAndFasterCooking() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        BuildingPrototype vanilla = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.FURNACE));
        BuildingPrototype steelFurnace = new BuildingPrototype(
                vanilla.id(), vanilla.label(), vanilla.cost(), vanilla.placementRule(), vanilla.texture(), 1, 1, 10, 2, true,
                vanilla.behavior(), vanilla.restoreBehavior(), vanilla.codec(),
                // Same id as vanilla.id() (the default anyway) plus the vanilla FURNACE's own fuel
                // item — this fixture reuses the shared FURNACE pool and still needs COAL, unlike
                // the 11-arg convenience constructor's fuel-less default.
                vanilla.id(), VanillaItems.COAL);
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES, steelFurnace);
        assertTrue(furnace.accept(world, VanillaItems.COAL));

        for (int i = 0; i < 6; i++) {
            assertTrue(furnace.accept(world, VanillaItems.IRON_ORE),
                    "buffer 10 must hold more than the vanilla default of 5 — this is the 6th unit");
        }

        int fastTime = Math.max(1, IRON_TIME / 2); // speedMultiplier halves the recipe's own time, floored
        for (int i = 0; i < fastTime - 1; i++) {
            furnace.tick(world, 0, 0);
        }
        assertEquals(0, chest.count(), "must not finish before the sped-up time");
        furnace.tick(world, 0, 0);
        assertEquals(1, chest.count(), "must finish in roughly half the vanilla recipe time");
    }

    /**
     * A furnace whose prototype declares demand must stop without coverage — the same gate Miner
     * already applies. Without this, a JSON {@code "power": { "demand": N }} on an assembler was
     * either rejected or (historically) silently ignored.
     */
    @Test
    void aFurnaceWithPowerDemandReportsNoPowerWhenUncovered() {
        BuildingPrototype vanilla = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.FURNACE));
        BuildingPrototype powered = new BuildingPrototype(
                ContentId.of("test:powered_furnace"),
                "Powered Furnace",
                vanilla.cost(),
                vanilla.placementRule(),
                vanilla.texture(),
                vanilla.footprintWidth(),
                vanilla.footprintHeight(),
                vanilla.bufferMax(),
                vanilla.speedMultiplier(),
                vanilla.acceptsSpeedEffects(),
                vanilla.behavior(),
                vanilla.restoreBehavior(),
                vanilla.codec(),
                vanilla.recipeKind(),
                vanilla.fuelItem(),
                Traits.one(VanillaTraits.POWER, PowerSpec.consumer(10)));
        World world = new World(4, 4);
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES, powered);
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        assertTrue(furnace.accept(world, VanillaItems.COAL));

        furnace.tick(world, 1, 1);

        assertEquals(BuildingStatus.NO_POWER, furnace.status(),
                "no pole covers (1,1), so a demanding furnace must not cook");
    }

    @Test
    void inspectionNamesEachInputAndWhatIsStillMissing() {
        // Live feedback: a multi-input machine used to say only "Ore buffer: 1" / NO_INPUT — the
        // player could not tell WHICH ingredient was missing. Alloy plate is the vanilla two-input
        // furnace recipe (iron plate + bronze plate).
        World world = new World(4, 4);
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, VanillaItems.IRON_PLATE));
        assertTrue(furnace.accept(world, VanillaItems.COAL));
        furnace.tick(world, 1, 1);

        List<String> lines = furnace.inspectionDetails(world, 1, 1);

        assertTrue(lines.stream().anyMatch(line -> line.contains("Making:") || line.contains("Selected:")
                        || line.contains("Recipe:")),
                "recipe identity must be visible: " + lines);
        assertTrue(lines.contains("  Iron Plate: 1"), "buffered input must be named: " + lines);
        assertTrue(lines.contains("  Bronze Plate: 0"), "empty input slot must show zero: " + lines);
        assertTrue(lines.contains("Waiting for: Bronze Plate"),
                "must name the missing ingredient, not a bare NO_INPUT: " + lines);
    }

    /** A distinct id per fixture recipe — recipes are addressable content now, and a fixture still has to say which one it means. */
    private static ContentId testRecipeId(int index) {
        return new ContentId("test", "fixture_recipe_" + index);
    }

    /** A vanilla technology's price, read from the registry the game itself researches through. */
    private static int costOf(com.rustorio.api.content.ContentId tech) {
        return VanillaTechs.frozen().get(tech).cost();
    }
}
