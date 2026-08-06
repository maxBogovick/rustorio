package com.rustorio.domain.building;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * There are two ways to ask a building what it is doing — {@link Building#status()} and {@link
 * Building#appearance()}{@code .status()} — and they must never disagree. The fast one exists only
 * because the world reads it for every building every tick and building a whole {@code Appearance}
 * for one field was the largest per-tick cost a pipe had; the moment the two drift, the HUD's
 * per-status counts start describing a factory that does not exist, and nothing else would notice.
 *
 * <p>Walks every registered vanilla prototype rather than a hand-listed few, so an archetype added
 * later is covered the day it is registered — the failure this guards against is precisely someone
 * overriding one of the two and forgetting the other.
 */
class BuildingStatusReportTest {

    @Test
    void everyVanillaArchetypeReportsTheSameStatusBothWays() {
        BuildingFactory factory = new BuildingFactory(new DepletedLayout(), RecipeBook.standard());

        for (BuildingType type : BuildingType.values()) {
            Building building = factory.create(VanillaBuildings.idFor(type), Direction.RIGHT);
            assertEquals(building.appearance().status(), building.status(),
                    type + ": the cheap status and the drawn one must be the same answer");
        }
    }

    /**
     * Fresh buildings mostly sit in one status, so agreeing on it proves little on its own. These
     * two are driven into a status they have to COMPUTE and then cache in a field — a boiler with
     * nothing to burn, a furnace with nothing to smelt — because a class that caches a field for
     * {@code status()} while {@code appearance()} recomputes is exactly where the two come apart.
     */
    @Test
    void aBuildingDrivenIntoAFailureStateStillReportsItBothWays() {
        World world = new World(20, 20, new BuildingFactory(new DepletedLayout(), RecipeBook.standard()));

        world.place(BuildingType.BOILER, 7, 5, Direction.RIGHT);
        world.place(BuildingType.FURNACE, 9, 5, Direction.RIGHT);
        // More than one tick: several archetypes spend a cooldown before they ever reach for what
        // they are missing, so a single tick would catch them merely counting down.
        for (int i = 0; i < 5; i++) {
            world.tick();
        }

        assertBothAgree(world, 7, 5, BuildingStatus.NO_FUEL);
        assertBothAgree(world, 9, 5, BuildingStatus.NO_INPUT);
    }

    private static void assertBothAgree(World world, int x, int y, BuildingStatus expected) {
        Building building = world.peek(x, y).orElseThrow();
        assertEquals(expected, building.status(),
                "the fixture is meant to drive this building into " + expected);
        assertEquals(building.appearance().status(), building.status(),
                "both ways of asking must give the same answer at (" + x + ", " + y + ")");
    }

    /**
     * Ore everywhere on paper, nothing left to take out of it — a worked-out patch. Reporting ore
     * satisfies {@code NEEDS_ORE} so a miner can be placed at all; refusing to yield any is what
     * drives it into {@code NO_ORE} on its first tick, which is the status this test needs.
     */
    private static final class DepletedLayout implements OreLayout {
        @Override
        public Optional<ItemType> oreAt(int x, int y) {
            return Optional.of(VanillaItems.IRON_ORE);
        }

        @Override
        public Optional<ItemType> extract(int x, int y) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemType> terrainAt(int x, int y) {
            return Optional.empty();
        }

        @Override
        public OreLayoutId id() {
            return new OreLayoutId("status-report-depleted", 0, 0, 0);
        }

        @Override
        public Map<Integer, Integer> depletionSnapshot() {
            return Map.of();
        }

        @Override
        public void restoreDepletion(Map<Integer, Integer> snapshot) {
            // never saved or loaded — this layout only exists for one assertion
        }
    }
}
