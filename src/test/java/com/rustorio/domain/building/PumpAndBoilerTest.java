package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.TerrainPatch;
import com.rustorio.domain.VanillaFluids;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chain the engine exists to make possible, end to end for the first time: a pump on the shore
 * fills a pipe with water, a boiler burns coal to turn that water into steam in a second, separate
 * network. Until this ran, fluid could only be injected by a test.
 *
 * <p>The case worth the most attention is the boiler with nowhere to put its steam. A machine that
 * drew water first and discovered afterwards that the output was full would destroy that water
 * silently, every tick, in exactly the situation a real factory spends most of its time in — a
 * backed-up line. {@link #aBoilerWithNowhereToPutSteamDoesNotDestroyTheWater} is that case.
 */
class PumpAndBoilerTest {

    /** A map that is ordinary ground everywhere except one lake, so a pump has a shore to stand on. */
    private static final int LAKE_X = 3;
    private static final int LAKE_Y = 5;

    @Test
    void aPumpOnTheShoreFillsThePipeItFaces() {
        World world = shoreWorld();
        assertTrue(world.place(BuildingType.PIPE, 5, 5));
        assertTrue(world.place(BuildingType.PUMP, 4, 5, Direction.RIGHT), "the cell right of the lake is shore");

        tick(world, 10); // one pump cycle

        assertEquals(VanillaFluids.WATER, portInto(world, 5, 5).fluid());
        assertEquals(100, portInto(world, 5, 5).amount(), "one cycle lifts one batch");
    }

    @Test
    void aPumpCannotBeBuiltAwayFromWater() {
        World world = shoreWorld();

        assertFalse(world.place(BuildingType.PUMP, 15, 15, Direction.RIGHT),
                "inland ground has no water within reach — the rule is what makes shoreline worth anything");
    }

    @Test
    void aPumpStopsReportingWorkingOnceTheNetworkIsFull() {
        World world = shoreWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PUMP, 4, 5, Direction.RIGHT);

        tick(world, 10 * 3); // one pipe holds 100; three cycles offer thrice what fits

        assertEquals(100, portInto(world, 5, 5).amount(), "a full network takes nothing more");
        assertEquals(BuildingStatus.OUTPUT_FULL, statusAt(world, 4, 5),
                "and the pump says why it stopped instead of looking healthy");
    }

    @Test
    void aBoilerTurnsWaterFromOneNetworkIntoSteamInAnother() {
        World world = shoreWorld();
        // pipe(5,5) — boiler(6,5) facing right — pipe(7,5). Two networks, the boiler between them.
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.BOILER, 6, 5, Direction.RIGHT);
        world.place(BuildingType.PIPE, 7, 5);
        world.place(BuildingType.PUMP, 4, 5, Direction.RIGHT);
        feedCoal(world, 6, 5, 1);

        tick(world, 40); // several pump cycles, so there is water to convert before the boiler runs dry

        FluidPort steam = portInto(world, 7, 5);
        assertEquals(VanillaFluids.STEAM, steam.fluid(), "the far side holds steam, not water");
        assertTrue(steam.amount() > 0, "the boiler actually moved something");
        assertEquals(VanillaFluids.WATER, portInto(world, 5, 5).fluid(),
                "and the near side is still water — the two never mix");
    }

    @Test
    void aBoilerWithoutFuelConvertsNothing() {
        World world = shoreWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.BOILER, 6, 5, Direction.RIGHT);
        world.place(BuildingType.PIPE, 7, 5);
        world.place(BuildingType.PUMP, 4, 5, Direction.RIGHT);

        tick(world, 40); // no coal was ever delivered

        assertEquals(0, portInto(world, 7, 5).amount(), "no fuel, no steam");
        assertEquals(BuildingStatus.NO_FUEL, statusAt(world, 6, 5));
        assertEquals(100, portInto(world, 5, 5).amount(), "and the water it could not convert is untouched");
    }

    @Test
    void aBoilerWithNowhereToPutSteamDoesNotDestroyTheWater() {
        World world = shoreWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.BOILER, 6, 5, Direction.RIGHT);
        // Deliberately NO pipe on the output side: the steam has nowhere to go at all. This is the
        // EASY half of the problem — the boiler never reaches the transfer, so the water is safe
        // whatever order the transfer itself uses. The hard half is the test below.
        world.place(BuildingType.PUMP, 4, 5, Direction.RIGHT);
        feedCoal(world, 6, 5, 5);

        tick(world, 60);

        assertEquals(100, portInto(world, 5, 5).amount(),
                "a boiler that cannot deliver steam must not consume water — the water side is untouched");
        assertEquals(BuildingStatus.OUTPUT_FULL, statusAt(world, 6, 5));
    }

    /**
     * The case the one above does NOT cover, and the reason both exist. With no output pipe at all
     * the boiler bails out early and the water is safe by accident; with a pipe that is merely FULL
     * it gets all the way to the transfer, and only asking about room BEFORE drawing water keeps it
     * from being swallowed. Written after the first version of this test passed against a
     * deliberately broken boiler.
     */
    @Test
    void aBoilerWhoseOutputPipeIsFullDoesNotDrawWaterItCannotConvert() {
        World world = shoreWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.BOILER, 6, 5, Direction.RIGHT);
        world.place(BuildingType.PIPE, 7, 5);
        feedCoal(world, 6, 5, 5);
        // No pump on purpose: one topping the water pipe back up every cycle would hide exactly the
        // loss this test is looking for. Both sides are filled by hand instead, and nothing refills
        // them — so any water that disappears, stays disappeared.
        assertEquals(100, portInto(world, 5, 5).insert(VanillaFluids.WATER, 100));
        assertEquals(100, portInto(world, 7, 5).insert(VanillaFluids.STEAM, 100));

        tick(world, 60);

        assertEquals(100, portInto(world, 5, 5).amount(),
                "the steam side had no room, so not one unit of water may have been drawn");
        assertEquals(100, portInto(world, 7, 5).amount(), "and the steam side is exactly as full as it was");
        assertEquals(BuildingStatus.OUTPUT_FULL, statusAt(world, 6, 5));
    }

    @Test
    void aBoilerRefusesAnythingThatIsNotItsFuel() {
        World world = shoreWorld();
        world.place(BuildingType.BOILER, 6, 5, Direction.RIGHT);
        Boiler boiler = (Boiler) world.peek(6, 5).orElseThrow();

        assertFalse(boiler.accept(world, VanillaItems.IRON_ORE), "a belt of ore must not clog the boiler");
        assertEquals(0, boiler.fuelBuffer());
        assertTrue(boiler.accept(world, VanillaItems.COAL));
        assertEquals(1, boiler.fuelBuffer());
    }

    /** Hands {@code count} coal straight to the boiler, standing in for the belt or inserter that would in a real factory. */
    private static void feedCoal(World world, int x, int y, int count) {
        Boiler boiler = (Boiler) world.peek(x, y).orElseThrow();
        for (int i = 0; i < count; i++) {
            assertTrue(boiler.accept(world, VanillaItems.COAL), "the boiler's fuel buffer must have room");
        }
    }

    private static void tick(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.tick();
        }
    }

    private static BuildingStatus statusAt(World world, int x, int y) {
        return world.peek(x, y).orElseThrow().appearance().status();
    }

    private static FluidPort portInto(World world, int x, int y) {
        Optional<FluidPort> port = world.fluidPort(x - 1, y, Direction.RIGHT);
        assertTrue(port.isPresent(), "expected a fluid tile at (" + x + ", " + y + ")");
        return port.orElseThrow();
    }

    /**
     * A world on an authored map whose only feature is a one-cell lake at {@code (LAKE_X, LAKE_Y)}
     * — the standard generated map's own water sits hundreds of cells out, far past anywhere a test
     * wants to build, and hunting for a shoreline in it would make these tests depend on that map's
     * exact layout.
     */
    private static World shoreWorld() {
        AuthoredMap map = new AuthoredMap(ContentId.of("test:shore"), List.of(),
                List.of(new TerrainPatch(LAKE_X, LAKE_Y, 0, VanillaItems.WATER)));
        BuildingFactory factory = new BuildingFactory(AuthoredOreLayout.from(map), RecipeBook.standard());
        return new World(20, 20, factory);
    }
}
