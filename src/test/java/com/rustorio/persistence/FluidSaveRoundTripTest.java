package com.rustorio.persistence;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.vanilla.VanillaFluids;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Boiler;
import com.rustorio.domain.building.BoilerState;
import com.rustorio.domain.building.FluidPort;
import com.rustorio.domain.building.PowerNetwork;
import com.rustorio.domain.building.PowerNode;
import com.rustorio.domain.world.World;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fluid network is never written to a save — only each tile's own share is, and the network is
 * rebuilt from the tiles' geometry on load, exactly the way belt segments already are. That makes
 * one identity load-bearing: the shares have to add back up to what the network held. If they do
 * not, fluid quietly appears or evaporates on every save/load, which no round trip of a single
 * building would ever notice.
 *
 * <p>The volumes below are chosen so the split does NOT come out even — 250 across three pipes is
 * 83, 83, 84 — because an amount that divides cleanly would pass even if the remainder were dropped
 * on the floor.
 */
class FluidSaveRoundTripTest {

    @Test
    void aNetworksVolumeSurvivesSaveAndLoadDownToTheLastUnit(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(20, 20);
        for (int i = 0; i < 3; i++) {
            world.place(BuildingType.PIPE, 5 + i, 5);
        }
        portInto(world, 5, 5).insert(VanillaFluids.WATER, 250);

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(20, 20);
        assertTrue(repository.load(reloaded).succeeded());

        FluidPort restored = portInto(reloaded, 5, 5);
        assertEquals(250, restored.amount(), "the tiles' shares have to add back up to what the network held");
        assertEquals(VanillaFluids.WATER, restored.fluid());
        assertEquals(300, restored.capacity(), "and the three tiles have to have found each other again");
        assertSame(portInto(reloaded, 7, 5), restored, "one network, not three tiles that each kept their own share");
    }

    @Test
    void twoNetworksOfDifferentFluidsComeBackAsTwoNetworks(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(20, 20);
        // Built apart, filled with different fluids, and only THEN joined by the tile between them:
        // the two refuse to merge, so the run is two networks with a border in the middle.
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PIPE, 7, 5);
        portInto(world, 5, 5).insert(VanillaFluids.WATER, 60);
        portInto(world, 7, 5).insert(VanillaFluids.STEAM, 40);
        world.place(BuildingType.PIPE, 6, 5);
        assertNotSame(portInto(world, 5, 5), portInto(world, 7, 5), "water and steam did not merge when joined");

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(20, 20);
        assertTrue(repository.load(reloaded).succeeded());

        FluidPort water = portInto(reloaded, 5, 5);
        FluidPort steam = portInto(reloaded, 7, 5);
        assertNotSame(water, steam, "geometry alone would have merged these — each tile's own fluid is what keeps them apart");
        assertEquals(VanillaFluids.WATER, water.fluid());
        assertEquals(60, water.amount());
        assertEquals(VanillaFluids.STEAM, steam.fluid());
        assertEquals(40, steam.amount());
    }

    /**
     * A grid is not written to a save any more than a fluid network is — it is rebuilt from where
     * the poles stand. Which means the thing to check is not a field but a shape: poles that were
     * one grid before the save have to be one grid after it, or a factory would come back cut in
     * half at exactly the places the player could not see.
     */
    @Test
    void agridComesBackAsOneGridBecauseThePolesComeBackWhereTheyStood(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(20, 20);
        world.place(BuildingType.POLE, 3, 3);
        world.place(BuildingType.POLE, 8, 3); // within reach of the first
        world.place(BuildingType.POLE, 15, 3); // out of reach of both — its own grid
        world.place(BuildingType.GENERATOR, 4, 4, Direction.RIGHT);

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(20, 20);
        assertTrue(repository.load(reloaded).succeeded());

        assertSame(gridAt(reloaded, 3, 3), gridAt(reloaded, 8, 3), "poles in reach are one grid again");
        assertNotSame(gridAt(reloaded, 3, 3), gridAt(reloaded, 15, 3), "and the far one is still separate");
        assertEquals(Direction.RIGHT, reloaded.peek(4, 4).orElseThrow().outputDirection().orElseThrow(),
                "the generator still draws from the side it was built facing");
    }

    private static PowerNetwork gridAt(World world, int x, int y) {
        PowerNode pole = (PowerNode) world.peek(x, y).orElseThrow();
        PowerNetwork grid = pole.network();
        assertTrue(grid != null, "a restored pole belongs to a grid, same as a placed one");
        return grid;
    }

    /**
     * A boiler's fuel is the one piece of its state a reload could quietly hand back for free.
     * Restarting the piece currently burning on every load would turn save-and-load into an infinite
     * fuel supply, which is why {@code burnTicksLeft} is written down rather than reset.
     */
    @Test
    void aBoilersFuelAndTheBurnAlreadySpentSurviveALoad(@TempDir Path dir) {
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        World world = new World(20, 20);
        world.place(BuildingType.BOILER, 5, 5, Direction.RIGHT);
        Boiler boiler = (Boiler) world.peek(5, 5).orElseThrow();
        assertTrue(boiler.accept(world, VanillaItems.COAL));
        assertTrue(boiler.accept(world, VanillaItems.COAL));
        BoilerState before = boiler.state();

        assertTrue(repository.save(world).succeeded());
        World reloaded = new World(20, 20);
        assertTrue(repository.load(reloaded).succeeded());

        Boiler restored = (Boiler) reloaded.peek(5, 5).orElseThrow();
        assertEquals(before, restored.state(), "fuel on hand and burn already spent both come back as they were");
        assertEquals(2, restored.fuelBuffer());
    }

    /**
     * The order the player happened to build in must not survive into the file. It is the one thing
     * that varies between two factories that are otherwise the same, and a network that handed out
     * shares in insertion order — the obvious implementation — would write two different files for
     * the same layout, so a save would stop being reproducible the moment anyone rebuilt a run.
     */
    @Test
    void thePerTileSharesAFileRecordsDoNotDependOnTheOrderThePipesWereBuiltIn(@TempDir Path dir) throws Exception {
        Path leftToRight = dir.resolve("left-to-right.json");
        Path rightToLeft = dir.resolve("right-to-left.json");

        World first = new World(20, 20);
        for (int x = 5; x <= 8; x++) {
            first.place(BuildingType.PIPE, x, 5);
        }
        portInto(first, 5, 5).insert(VanillaFluids.WATER, 250); // does not divide evenly by four
        assertTrue(new JsonSaveRepository(leftToRight).save(first).succeeded());

        World second = new World(20, 20);
        for (int x = 8; x >= 5; x--) {
            second.place(BuildingType.PIPE, x, 5);
        }
        portInto(second, 5, 5).insert(VanillaFluids.WATER, 250);
        assertTrue(new JsonSaveRepository(rightToLeft).save(second).succeeded());

        assertEquals(Files.readString(leftToRight), Files.readString(rightToLeft),
                "the same layout holding the same volume has to produce the same file, whichever end it was built from");
    }

    /**
     * A pipe naming a fluid nobody registers any more — the mod that added it was removed — must not
     * take the whole save down with it. A missing PROTOTYPE already degrades gracefully (that cell
     * comes back empty, the rest of the factory loads), and a missing fluid is the same class of
     * loss: content that is merely absent, not a file that is unreadable. Rejecting outright would
     * throw away every belt, chest and furnace the save still remembers to protect nothing.
     *
     * <p>The tile itself survives — a pipe is a pipe whatever used to be inside it — and comes back
     * empty. What it was holding IS lost, and that is a real loss this test pins deliberately rather
     * than hides: reporting it to the player needs a place to put it that today's save result has no
     * field for.
     */
    @Test
    void aPipeHoldingAFluidWhoseModIsGoneLoadsAsAnEmptyPipeRatherThanFailingTheSave(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("save.json");
        World world = new World(20, 20);
        world.place(BuildingType.PIPE, 5, 5);
        portInto(world, 5, 5).insert(VanillaFluids.WATER, 60);
        world.place(BuildingType.CHEST, 9, 9); // an ordinary building the save must not lose over this
        assertTrue(new JsonSaveRepository(file).save(world).succeeded());

        // Exactly what uninstalling the mod that owned the fluid would leave behind in the file.
        Files.writeString(file, Files.readString(file).replace("rustorio:water", "nosuchmod:oil"));

        World reloaded = new World(20, 20);
        assertTrue(new JsonSaveRepository(file).load(reloaded).succeeded(),
                "an absent fluid must not reject a save the way an unreadable file does");
        assertTrue(reloaded.peek(9, 9).isPresent(), "the rest of the factory still loaded");
        FluidPort restored = portInto(reloaded, 5, 5);
        assertEquals(null, restored.fluid(), "the pipe is still there, holding nothing it cannot name");
        assertEquals(0, restored.amount(), "and holding no volume of a fluid that no longer exists");
    }

    private static FluidPort portInto(World world, int x, int y) {
        Optional<FluidPort> port = world.fluidPort(x - 1, y, Direction.RIGHT);
        assertTrue(port.isPresent(), "expected a fluid tile at (" + x + ", " + y + ")");
        return port.orElseThrow();
    }
}
