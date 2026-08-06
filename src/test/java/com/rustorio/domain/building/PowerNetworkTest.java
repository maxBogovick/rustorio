package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OrePatch;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaFluids;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Electricity end to end, and the two properties that make it worth having at all: it is OPT-IN, so
 * a factory built before there was any power keeps running untouched, and a grid is something the
 * player lays out with poles rather than something machines arrange among themselves.
 *
 * <p>{@link #anOrdinaryMinerKeepsWorkingWithNoElectricityAnywhere} is the one that would catch the
 * change nobody wants: the moment power stops being opt-in, every existing map breaks at once, and
 * nothing else here would notice.
 */
class PowerNetworkTest {

    /** The vanilla generator's output and the electric miner's demand — see {@code VanillaBuildings}. One generator carries ten miners. */
    private static final long GENERATOR_OUTPUT = 100;
    private static final long MINER_DEMAND = 10;

    @Test
    void anOrdinaryMinerKeepsWorkingWithNoElectricityAnywhere() {
        World world = oreWorld();
        world.place(BuildingType.MINER, 5, 5, Direction.RIGHT);
        world.place(BuildingType.CHEST, 6, 5);

        tick(world, 30);

        assertNotSame(BuildingStatus.NO_POWER, statusAt(world, 5, 5),
                "a building that declares no power demand must never be gated on power — this is what "
                        + "lets every map built before electricity existed load and run unchanged");
        assertTrue(chestAt(world, 6, 5).count() > 0, "and it must actually still be mining");
    }

    @Test
    void anElectricMinerWithNoGridAtAllStopsAndSaysWhy() {
        World world = oreWorld();
        world.place(BuildingType.ELECTRIC_MINER, 5, 5, Direction.RIGHT);
        world.place(BuildingType.CHEST, 6, 5);

        tick(world, 30);

        assertEquals(BuildingStatus.NO_POWER, statusAt(world, 5, 5));
        assertEquals(0, chestAt(world, 6, 5).count(), "an unpowered machine produces nothing at all");
    }

    @Test
    void aGeneratorBurningSteamPowersAnElectricMinerThroughAPole() {
        World world = oreWorld();
        world.place(BuildingType.ELECTRIC_MINER, 5, 5, Direction.RIGHT);
        world.place(BuildingType.CHEST, 6, 5);
        world.place(BuildingType.POLE, 7, 5);
        // The generator draws steam from what it faces, filled by hand here, standing in for the
        // boiler proven in its own test. A TANK, not a pipe: a pipe holds 100 and a generator burns
        // 5 a tick, so a pipe would run dry after twenty ticks and this test would be measuring how
        // long the steam lasts instead of whether power reaches the miner.
        world.place(BuildingType.GENERATOR, 8, 5, Direction.RIGHT);
        world.place(BuildingType.TANK, 9, 5);
        portInto(world, 9, 5).insert(VanillaFluids.STEAM, 2000);

        tick(world, 30);

        assertEquals(BuildingStatus.WORKING, statusAt(world, 5, 5), "the chain closes: steam becomes ore");
        assertTrue(chestAt(world, 6, 5).count() > 0);
    }

    @Test
    void agridShortOfPowerRunsTheMachinesItCanReachAndDarkensTheRest() {
        World world = oreWorld();
        // Eleven electric miners on one grid ask for 110 a tick; one generator makes 100, so exactly
        // ten of them can run — and it must be the same ten every tick, by coordinate. The row and
        // the pole are centred on x=10 so all eleven cells (5..15) sit inside one pole's radius-5 reach.
        for (int i = 0; i < 11; i++) {
            assertTrue(world.place(BuildingType.ELECTRIC_MINER, 5 + i, 5, Direction.DOWN),
                    "the whole row must sit on ore");
        }
        world.place(BuildingType.POLE, 10, 6);
        world.place(BuildingType.GENERATOR, 10, 8, Direction.RIGHT);
        world.place(BuildingType.TANK, 11, 8); // holds enough steam that this test never measures fuel
        portInto(world, 11, 8).insert(VanillaFluids.STEAM, 2000);

        tick(world, 30);

        assertEquals(GENERATOR_OUTPUT / MINER_DEMAND, poweredCount(world, 11),
                "as many machines run as the grid can actually carry, not all of them at a fraction");
        // The world ticks machines like these from high coordinates to low (TickScheduler's
        // descending pass), so the FIRST to ask is the far end of the row and the one left dark is
        // the near end. Which end matters less than that it is always the same end: a browned-out
        // factory has a fixed dark machine to go look at, not a different random one each tick.
        assertEquals(BuildingStatus.NO_POWER, statusAt(world, 5, 5),
                "the machine that draws last goes dark, and it is the same one every tick");
        assertNotSame(BuildingStatus.NO_POWER, statusAt(world, 15, 5), "the one that draws first always runs");
    }

    @Test
    void poleWithinReachOfAnotherJoinsTheSameGridAndDemolishingItSplitsThemAgain() {
        World world = oreWorld();
        world.place(BuildingType.POLE, 5, 10);
        world.place(BuildingType.POLE, 10, 10); // exactly five cells away — inside the radius
        world.place(BuildingType.POLE, 15, 10);

        assertSame(networkAt(world, 5, 10), networkAt(world, 15, 10),
                "three poles in a chain are one grid even though the ends cannot see each other");
        // Identity alone would also pass for a merge that quietly dropped a pole on the way: the
        // two ends would still answer with one network, just one that has forgotten a member. The
        // count is what tells "merged" from "merged and lost something".
        assertEquals(3, networkAt(world, 5, 10).poleCount(), "all three poles are members, not just the ends");

        world.removeBuilding(10, 10);

        assertNotSame(networkAt(world, 5, 10), networkAt(world, 15, 10),
                "taking out the middle pole leaves two grids, exactly as cutting a pipe leaves two networks");
        assertEquals(1, networkAt(world, 5, 10).poleCount(), "and each half keeps exactly its own pole");
        assertEquals(1, networkAt(world, 15, 10).poleCount());
    }

    @Test
    void aPoleTooFarAwayStartsItsOwnGrid() {
        World world = oreWorld();
        world.place(BuildingType.POLE, 5, 10);
        world.place(BuildingType.POLE, 11, 10); // six cells — one past the radius

        assertNotSame(networkAt(world, 5, 10), networkAt(world, 11, 10));
    }

    @Test
    void powerIsNotStorableSoAnIdleTicksOutputIsGoneRatherThanBanked() {
        World world = oreWorld();
        world.place(BuildingType.POLE, 7, 5);
        world.place(BuildingType.GENERATOR, 8, 5, Direction.RIGHT);
        world.place(BuildingType.TANK, 9, 5);
        portInto(world, 9, 5).insert(VanillaFluids.STEAM, 2000);

        tick(world, 10); // ten ticks of output with nothing drawing any of it

        assertEquals(GENERATOR_OUTPUT, networkAt(world, 7, 5).supplied(world.currentTick()),
                "a grid holds one tick's output and no more — banking it would be an accumulator, "
                        + "which is a building nobody has built");
    }

    /**
     * Poles overlap constantly — a player laying out a grid does not measure gaps — so which of two
     * covering poles a machine draws through has to be decided by a fixed rule and, more to the
     * point, has to survive one of them being demolished. Taking out the pole that happened to own
     * a shared cell must hand that cell to the other, not leave it dark: the machine standing there
     * is still inside a pole's radius, and the player has no way to see which pole was "its" one.
     */
    @Test
    void aCellTwoPolesBothCoverStaysPoweredWhenOneOfThemIsDemolished() {
        World world = oreWorld();
        world.place(BuildingType.POLE, 4, 4);
        world.place(BuildingType.POLE, 6, 4); // two apart — their squares overlap heavily
        world.place(BuildingType.GENERATOR, 4, 6, Direction.RIGHT);
        world.place(BuildingType.TANK, 5, 6); // on the side the generator faces, so it draws steam
        portInto(world, 5, 6).insert(VanillaFluids.STEAM, 2000);
        world.place(BuildingType.ELECTRIC_MINER, 5, 5, Direction.RIGHT); // in both poles' reach

        tick(world, 1);
        assertNotSame(BuildingStatus.NO_POWER, statusAt(world, 5, 5), "covered by both poles, so it runs");

        world.removeBuilding(4, 4); // the smaller cell — the one that owned the shared coverage
        tick(world, 1);

        assertNotSame(BuildingStatus.NO_POWER, statusAt(world, 5, 5),
                "the remaining pole still covers this cell, so demolishing the other must not darken it");
    }

    /** How many of the {@code count} electric miners in the row at y=5 actually ran this tick. */
    private static int poweredCount(World world, int count) {
        int powered = 0;
        for (int i = 0; i < count; i++) {
            if (statusAt(world, 5 + i, 5) != BuildingStatus.NO_POWER) {
                powered++;
            }
        }
        return powered;
    }

    private static PowerNetwork networkAt(World world, int x, int y) {
        Building pole = world.peek(x, y).orElseThrow();
        assertTrue(pole instanceof PowerNode, "expected a pole at (" + x + ", " + y + ")");
        PowerNetwork network = ((PowerNode) pole).network();
        assertTrue(network != null, "a placed pole always belongs to a grid");
        return network;
    }

    private static Chest chestAt(World world, int x, int y) {
        return (Chest) world.peek(x, y).orElseThrow();
    }

    private static BuildingStatus statusAt(World world, int x, int y) {
        return world.peek(x, y).orElseThrow().appearance().status();
    }

    private static FluidPort portInto(World world, int x, int y) {
        Optional<FluidPort> port = world.fluidPort(x - 1, y, Direction.RIGHT);
        assertTrue(port.isPresent(), "expected a fluid tile at (" + x + ", " + y + ")");
        return port.orElseThrow();
    }

    private static void tick(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.tick();
        }
    }

    /** A world whose whole top-left corner is iron ore, so a row of miners can stand anywhere in it. */
    private static World oreWorld() {
        ItemType ore = VanillaItems.IRON_ORE;
        AuthoredMap map = new AuthoredMap(ContentId.of("test:ore_field"),
                List.of(new OrePatch(8, 5, 12, ore)), List.of());
        BuildingFactory factory = new BuildingFactory(AuthoredOreLayout.from(map), RecipeBook.standard());
        World world = new World(20, 20, factory);
        assertFalse(world.buildingFactory().oreLayout().oreAt(5, 5).isEmpty(), "the fixture must actually have ore");
        return world;
    }
}
