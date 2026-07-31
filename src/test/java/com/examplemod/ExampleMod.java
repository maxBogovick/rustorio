package com.examplemod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.BeltState;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.FurnaceState;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;

/**
 * A stand-in for a real mod's own registration code — the {@code
 * VanillaBuildings.registerAll}-shaped entry point a mod would provide, registering its OWN
 * prototypes into whatever {@link Registry} the surrounding {@code BuildingFactory} was built
 * with, next to the vanilla ones.
 *
 * <p>Two prototypes, matching the phase's own acceptance criterion:
 * <ul>
 *   <li>{@link #STEEL_PRESS_ID} — reuses {@link Furnace} outright (no new Java class needed for
 *   this half), just different data (bigger buffer, faster). Has no {@link BuildingType} of its
 *   own.</li>
 *   <li>{@link #CONVEYOR_ID} — backed by {@link ExampleModBelt}, a genuinely new class living
 *   outside {@code com.rustorio.domain.building} that implements the transport capability from
 *   scratch.</li>
 * </ul>
 */
final class ExampleMod {

    static final ContentId STEEL_PRESS_ID = ContentId.of("examplemod:steel_press");
    static final ContentId CONVEYOR_ID = ContentId.of("examplemod:conveyor");

    private ExampleMod() {
    }

    static void registerAll(Registry<BuildingPrototype> prototypes) {
        prototypes.register(STEEL_PRESS_ID, new BuildingPrototype(
                STEEL_PRESS_ID,
                "Steel Press",
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.FURNACE_COLD,
                10, // buffer — double the vanilla PRESS's 5
                2, // speed multiplier — twice as fast
                true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(BuildingType.PRESS, state, factory.recipeBook(), self);
                },
                // Same Codec the vanilla PRESS prototype uses — FurnaceState's shape doesn't depend
                // on which prototype governs it, only on the archetype (Furnace) reused here.
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec()));
        prototypes.register(CONVEYOR_ID, new BuildingPrototype(
                CONVEYOR_ID,
                "Conveyor",
                new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.BELT_EMPTY,
                0, 1, false,
                (self, direction, factory) -> new ExampleModBelt(self.id(), direction),
                (self, decodedState, factory) -> {
                    BeltState state = (BeltState) decodedState;
                    return new ExampleModBelt(self.id(), state.direction(), state.held());
                },
                // Same Codec the vanilla BELT prototype uses — ExampleModBelt's own state has the
                // exact same shape (direction + held) as the vanilla Belt it stands in for.
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.BELT)).codec()));
    }
}
