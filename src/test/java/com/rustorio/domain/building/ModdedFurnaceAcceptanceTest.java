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
 * Phase capstone: both halves of the phase's own acceptance example together — a "steel press"
 * (a real {@link BuildingPrototype}, not a new {@code BuildingType} constant: bigger buffer,
 * twice the speed, a different cost) running a genuinely three-ingredient {@link Recipe} — plus a
 * save/load round trip proving the prototype identity survives it.
 *
 * <p>Built by calling {@link Furnace}'s constructor directly, not through {@link BuildingFactory}
 * — an owner decision: {@code BuildingFactory.create/restore}'s own dispatch (which Java class a
 * {@code BuildingType} builds) stays closed until behavior itself opens up in a later phase, so a
 * genuinely new prototype isn't reachable through it yet. This proves the DATA half of
 * moddability (a new prototype's numbers actually drive a live building), not the discovery half
 * (placing it through the normal hotbar/save-load-by-{@code BuildingType} path) — a deliberate,
 * not accidental, scope line.
 */
class ModdedFurnaceAcceptanceTest {

    private static final ContentId STEEL_PRESS_ID = ContentId.of("examplemod:steel_press");

    @Test
    void steelPressWithAThreeIngredientRecipeCooksFasterWithMoreBufferAndSurvivesSaveLoad() {
        BuildingPrototype steelPress = new BuildingPrototype(
                STEEL_PRESS_ID,
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.FURNACE_COLD,
                10, // buffer — double the vanilla PRESS's 5
                2, // speed multiplier — twice as fast
                true);

        Recipe tripleInput = new Recipe(
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

        // Save/load round trip: the memento must name this exact prototype, and restoring through
        // it (not the vanilla default for PRESS) must reproduce the same speed/buffer behavior.
        BuildingMemento.FurnaceState state = (BuildingMemento.FurnaceState) press.memento();
        assertEquals(STEEL_PRESS_ID, state.prototypeId(), "the memento must name the exact prototype this press was built with");

        Registry<BuildingPrototype> moddedPrototypes = new Registry<>();
        moddedPrototypes.register(STEEL_PRESS_ID, steelPress);
        moddedPrototypes.freeze();
        BuildingPrototype resolved = moddedPrototypes.get(state.prototypeId());
        Furnace restored = new Furnace(state, recipeBook, resolved);

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
}
