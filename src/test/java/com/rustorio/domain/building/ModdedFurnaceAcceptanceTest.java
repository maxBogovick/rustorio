package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.world.World;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 capstone: both halves of that phase's own acceptance example together — a "steel press"
 * (a real {@link BuildingPrototype}, not a new {@code BuildingType} constant: bigger buffer,
 * twice the speed, a different cost) running a genuinely three-ingredient {@link Recipe} — plus a
 * save/load round trip proving the prototype identity survives it.
 *
 * <p>Built by calling {@link Furnace}'s constructor directly, not through {@link BuildingFactory}
 * — that was an owner decision AT THE TIME (Phase 4): {@code BuildingFactory.create/restore}'s own
 * dispatch stayed closed until behavior itself opened up, so a genuinely new prototype wasn't
 * reachable through it yet. It is now ({@code BuildingFactory.create(ContentId, Direction)} — see
 * {@code BuildingFactoryTest} for the equivalent proof going through the real factory instead).
 * This test predates that and still proves what it originally set out to: the DATA half of
 * moddability (a new prototype's numbers actually drive a live building), not the discovery half —
 * left as-is rather than rewritten, since it still passes and still documents Phase 4's own
 * criterion honestly.
 */
class ModdedFurnaceAcceptanceTest {

    private static final ContentId STEEL_PRESS_ID = ContentId.of("examplemod:steel_press");

    @Test
    void steelPressWithAThreeIngredientRecipeCooksFasterWithMoreBufferAndSurvivesSaveLoad() {
        BuildingPrototype steelPress = new BuildingPrototype(
                STEEL_PRESS_ID,
                "Steel Press",
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.FURNACE_COLD,
                1, 1,
                10, // buffer — double the vanilla PRESS's 5
                2, // speed multiplier — twice as fast
                true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(BuildingType.PRESS, state, factory.recipeBook(), self);
                },
                // Same Codec instance the vanilla PRESS prototype uses — FurnaceState's shape
                // doesn't depend on which prototype governs it, only on the archetype (Furnace).
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec(),
                // The triple-input recipe below is tagged BuildingType.PRESS's own shared kind —
                // this prototype must search that SAME pool, not its own private default.
                VanillaBuildings.idFor(BuildingType.PRESS), null);

        Recipe tripleInput = new Recipe(testRecipeId(1), 
                List.of(VanillaItems.IRON_PLATE, VanillaItems.BRONZE_PLATE, VanillaItems.GEAR),
                VanillaItems.CHASSIS, 6, BuildingType.PRESS);
        RecipeBook recipeBook = new RecipeBook(List.of(tripleInput));

        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, recipeBook, steelPress);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));
        assertTrue(press.accept(world, VanillaItems.BRONZE_PLATE));
        assertTrue(press.accept(world, VanillaItems.GEAR));

        // effectiveTime = max(1, 6 / speedMultiplier 2) = 3, not the recipe's own 6 — proves speed
        // and the three-ingredient recipe are being read from data at the same time, not tripping
        // over each other.
        press.tick(world, 0, 0);
        press.tick(world, 0, 0);
        assertEquals(0, chest.count(), "must not finish before the sped-up time");
        press.tick(world, 0, 0);
        assertEquals(1, chest.amount(VanillaItems.CHASSIS), "must finish in half the recipe's own time");

        // Save/load round trip: the building must name this exact prototype, and restoring through
        // it (not the vanilla default for PRESS) must reproduce the same speed/buffer behavior.
        FurnaceState state = (FurnaceState) press.state();
        assertEquals(STEEL_PRESS_ID, press.prototypeId(), "the building must name the exact prototype this press was built with");

        Registry<BuildingPrototype> moddedPrototypes = new Registry<>();
        moddedPrototypes.register(STEEL_PRESS_ID, steelPress);
        moddedPrototypes.freeze();
        BuildingPrototype resolved = moddedPrototypes.get(press.prototypeId());
        Furnace restored = new Furnace(BuildingType.PRESS, state, recipeBook, resolved);

        restored.accept(world, VanillaItems.IRON_PLATE);
        restored.accept(world, VanillaItems.BRONZE_PLATE);
        restored.accept(world, VanillaItems.GEAR);
        // Same sped-up timing after restore — proves the round trip actually recovered the custom
        // prototype (speed 2x), not a silent fallback to PRESS's vanilla default (which would take
        // the recipe's full 6 ticks instead of 3).
        restored.tick(world, 0, 0);
        restored.tick(world, 0, 0);
        assertEquals(1, chest.amount(VanillaItems.CHASSIS), "still only the first batch — restored press must not have finished yet");
        restored.tick(world, 0, 0);
        assertEquals(2, chest.amount(VanillaItems.CHASSIS), "restored press must still cook at the sped-up rate, not the vanilla default");
    }

    /** A distinct id per fixture recipe — recipes are addressable content now, and a fixture still has to say which one it means. */
    private static ContentId testRecipeId(int index) {
        return new ContentId("test", "fixture_recipe_" + index);
    }
}
