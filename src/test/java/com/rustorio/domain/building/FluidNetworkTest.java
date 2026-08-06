package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.VanillaFluids;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three claims a fluid network is built on: connected plumbing is ONE volume rather than a
 * per-tile queue, placing a tile joins whatever it touches, and demolishing one can leave two
 * independent networks that must divide the fluid between them without inventing or losing any.
 *
 * <p>The split rule is the part worth testing hardest, because the obvious version of it is wrong:
 * dividing by tile COUNT hands a side more fluid than it can physically hold as soon as tiles
 * differ in size (a tank is one tile and holds twenty-five pipes' worth). Dividing by capacity is
 * what {@link FluidNetwork} actually does, and {@link #splitBetweenUnequalSidesNeverExceedsWhatASideHolds}
 * is the case that tells the two apart.
 *
 * <p>There are no producers yet — a pump and a boiler are their own step — so fluid enters through
 * the same {@link FluidPort} a machine would use, which is also what proves that port works.
 */
class FluidNetworkTest {

    private static final FluidType WATER = VanillaFluids.WATER;
    private static final FluidType STEAM = VanillaFluids.STEAM;

    /** Every pipe registered by the vanilla prototypes holds this much — see {@code VanillaBuildings}. */
    private static final long PIPE = 100;

    @Test
    void connectedPipesAreOneVolumeWithOneCombinedCapacity() {
        World world = new World(20, 20);
        placePipes(world, 3);

        FluidPort port = portInto(world, 5, 5);

        assertEquals(3 * PIPE, port.capacity(), "three pipes are one bucket, not three separate ones");
        assertEquals(3 * PIPE, port.insert(WATER, 3 * PIPE), "a network that empty takes everything offered");
        assertEquals(3 * PIPE, port.amount());
        assertSame(portInto(world, 7, 5), port, "every tile of one network answers with the same network");
    }

    @Test
    void aFullNetworkTakesOnlyWhatStillFitsAndReportsHowMuchThatWas() {
        World world = new World(20, 20);
        placePipes(world, 2);
        FluidPort port = portInto(world, 5, 5);

        assertEquals(2 * PIPE, port.insert(WATER, 10_000), "a partial transfer is the normal case, not a failure");
        assertEquals(0, port.insert(WATER, 1), "nothing more fits");
    }

    @Test
    void aNetworkHoldingOneFluidRefusesAnother() {
        World world = new World(20, 20);
        placePipes(world, 2);
        FluidPort port = portInto(world, 5, 5);
        port.insert(WATER, PIPE);

        assertEquals(0, port.insert(STEAM, PIPE), "water and steam never mix — there is no way to express a mixture");
        assertEquals(0, port.extract(STEAM, PIPE), "and nothing can pull out a fluid that isn't in there");
        assertEquals(PIPE, port.amount());
    }

    @Test
    void drainingANetworkCompletelyLetsItAcceptADifferentFluid() {
        World world = new World(20, 20);
        placePipes(world, 2);
        FluidPort port = portInto(world, 5, 5);
        port.insert(WATER, PIPE);

        assertEquals(PIPE, port.extract(WATER, PIPE));
        assertNull(port.fluid(), "an emptied network forgets what it held, which is what lets it be reused");
        assertEquals(PIPE, port.insert(STEAM, PIPE));
    }

    @Test
    void placingAPipeBetweenTwoNetworksMergesThemIntoOne() {
        World world = new World(20, 20);
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PIPE, 7, 5);
        portInto(world, 5, 5).insert(WATER, 40);
        portInto(world, 7, 5).insert(WATER, 60);

        world.place(BuildingType.PIPE, 6, 5); // closes the gap

        FluidPort merged = portInto(world, 5, 5);
        assertSame(portInto(world, 7, 5), merged, "the two runs are one network now");
        assertEquals(3 * PIPE, merged.capacity());
        assertEquals(100, merged.amount(), "merging pools what both sides held — nothing appears, nothing is lost");
    }

    @Test
    void demolishingTheMiddlePipeSplitsTheNetworkAndDividesTheFluid() {
        World world = new World(20, 20);
        placePipes(world, 5); // (5,5)..(9,5)
        portInto(world, 5, 5).insert(WATER, 200);

        world.removeBuilding(7, 5); // the middle tile leaves, carrying its own share

        FluidPort left = portInto(world, 5, 5);
        FluidPort right = portInto(world, 8, 5);
        assertNotSame(left, right, "the run is two independent networks now");
        assertEquals(2 * PIPE, left.capacity());
        assertEquals(2 * PIPE, right.capacity());
        // 200 across five equal pipes is 40 each: the demolished tile took 40 away with it and the
        // two remaining pairs hold 80 apiece.
        assertEquals(80, left.amount());
        assertEquals(80, right.amount());
    }

    @Test
    void splitBetweenUnequalSidesNeverExceedsWhatASideHolds() {
        World world = new World(20, 20);
        // tank — pipe — pipe: demolishing the middle leaves a 2500-capacity side and a 100 one.
        world.place(BuildingType.TANK, 5, 5);
        world.place(BuildingType.PIPE, 6, 5);
        world.place(BuildingType.PIPE, 7, 5);
        FluidPort whole = portInto(world, 5, 5);
        whole.insert(WATER, 2600); // full to the brim: 2500 + 100 + 100 = 2700 capacity

        world.removeBuilding(6, 5);

        FluidPort tankSide = portInto(world, 5, 5);
        FluidPort pipeSide = portInto(world, 7, 5);
        assertTrue(pipeSide.amount() <= pipeSide.capacity(),
                "dividing by tile COUNT would have given the lone pipe half of 2600 — thirteen times what it holds");
        assertTrue(tankSide.amount() <= tankSide.capacity());
        assertEquals(2600, tankSide.amount() + pipeSide.amount() + demolishedShare(2600, 2700),
                "every unit is accounted for: what stayed on each side plus what the demolished tile carried off");
    }

    @Test
    void demolishingAnEndPipeLeavesOneNetworkAndTakesOnlyItsOwnShare() {
        World world = new World(20, 20);
        placePipes(world, 3);
        portInto(world, 5, 5).insert(WATER, 300);

        world.removeBuilding(7, 5); // the far end, not the middle — nothing to split

        FluidPort rest = portInto(world, 5, 5);
        assertSame(portInto(world, 6, 5), rest, "removing an end tile leaves the rest one network");
        assertEquals(200, rest.amount(), "the tile left with its own 100, not with more and not with less");
    }

    /**
     * The case that proves a tile rejoining the map is weighed against ITS OWN fluid, not just
     * against the first network it happens to touch.
     *
     * <p>An emptied network accepts anything — that is what lets a fresh pipe join either side of a
     * boundary. But it must not become the doorway through which two INCOMPATIBLE networks are
     * merged: if the empty side is chosen first and then allowed to swallow a steam network, the
     * water the newcomer is carrying has nowhere left to go, and a water tank ends up a member of a
     * steam network. Both things the design promises are broken at once — "one fluid per network by
     * construction", and volume being conserved across a demolish/undo.
     *
     * <p>Undo of a demolition is the everyday way to reach it, which is why the fixture uses the
     * same {@code restoreBuilding} door {@code RemoveAction.undo} does rather than placing a new
     * tile: only a restore hands back a tile that is still carrying something.
     */
    @Test
    void restoredTileKeepsItsFluidWhenAnEmptyNeighborTouchesAForeignNetwork() {
        World world = new World(20, 20);
        // Water: a tank with one pipe to its right. The tank's capacity dwarfs the pipe's, so on
        // demolition it carries the whole 20 away and the pipe is left holding nothing.
        assertTrue(world.place(BuildingType.TANK, 5, 5));
        assertTrue(world.place(BuildingType.PIPE, 6, 5));
        portInto(world, 5, 5).insert(WATER, 20);

        // Steam, built away from the tank first so it is already full when it reaches the boundary:
        // (5, 6) touches the tank's DOWN side and stays a separate network because steam won't take
        // water. That border is the correct behavior this test does NOT want to change.
        assertTrue(world.place(BuildingType.PIPE, 5, 7));
        portInto(world, 5, 7).insert(STEAM, 50);
        assertTrue(world.place(BuildingType.PIPE, 5, 6));
        assertNotSame(portInto(world, 5, 5), portInto(world, 5, 6), "water and steam are two networks");

        Building tank = world.removeBuilding(5, 5).orElseThrow();
        assertNull(portInto(world, 6, 5).fluid(), "the pipe left behind holds nothing — an empty network");

        world.restoreBuilding(5, 5, tank);

        FluidPort restored = portInto(world, 5, 5);
        assertEquals(WATER, restored.fluid(), "the tank came back carrying water and must still be on water");
        assertEquals(20, restored.amount(), "the water it carried away is poured back, not destroyed");
        assertNotSame(restored, portInto(world, 5, 6), "the empty pipe must not have merged the steam network in");
        assertEquals(50, portInto(world, 5, 6).amount(), "the steam side is untouched by any of this");
    }

    /** How much a tile of {@code PIPE} capacity carries away out of a network holding {@code total} of {@code capacity}. */
    private static long demolishedShare(long total, long capacity) {
        return total * PIPE / capacity;
    }

    /** A straight run of {@code count} pipes starting at (5, 5) and heading right. */
    private static void placePipes(World world, int count) {
        for (int i = 0; i < count; i++) {
            assertTrue(world.place(BuildingType.PIPE, 5 + i, 5), "the standard map's row 5 must be buildable");
        }
    }

    /**
     * The port a machine standing at {@code (x - 1, y)} would see looking right — i.e. the network
     * on the tile at {@code (x, y)}. Goes through {@code World.fluidPort} deliberately: that is the
     * only door a machine will ever have, so the tests use it too.
     */
    private static FluidPort portInto(World world, int x, int y) {
        Optional<FluidPort> port = world.fluidPort(x - 1, y, Direction.RIGHT);
        assertTrue(port.isPresent(), "expected a fluid tile at (" + x + ", " + y + ")");
        return port.orElseThrow();
    }
}
