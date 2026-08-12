package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.BuildingVisibility;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.game.GameBootstrap;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vanilla progression gates: a fresh world shows only the starter four until iron plates are
 * produced.
 */
class VanillaProgressionAcceptanceTest {

    private static final Path RUSTORIO = Path.of("resources", "mods", "rustorio");

    @Test
    void aFreshWorldShowsOnlyStarterBuildingsInTheHudStrip() {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO));
        World world = GameBootstrap.createWorld(content, PatchOreLayout.standard(), 32, 32);

        assertAvailable(world, BuildingType.MINER);
        assertAvailable(world, BuildingType.CHEST);
        assertAvailable(world, BuildingType.FURNACE);
        assertAvailable(world, BuildingType.BELT);
        assertLocked(world, BuildingType.PRESS);
        assertLocked(world, BuildingType.GENERATOR);
        assertLocked(world, BuildingType.ELECTRIC_MINER);
    }

    private static void assertAvailable(World world, BuildingType type) {
        BuildingPrototype prototype = world.buildingFactory().buildings().get(VanillaBuildings.idFor(type));
        assertTrue(BuildingVisibility.isAvailable(prototype, world.visibilityContext()),
                type + " should be available");
    }

    private static void assertLocked(World world, BuildingType type) {
        BuildingPrototype prototype = world.buildingFactory().buildings().get(VanillaBuildings.idFor(type));
        assertFalse(BuildingVisibility.isAvailable(prototype, world.visibilityContext()),
                type + " should stay locked");
    }
}
