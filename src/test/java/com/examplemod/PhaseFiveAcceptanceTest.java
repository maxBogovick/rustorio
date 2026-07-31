package com.examplemod;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.TransportNode;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.JsonSaveRepository;
import com.rustorio.persistence.SaveRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5's own acceptance capstone: a mod adds (a) a building with no archetype of its own and
 * (b) a transport node that fuses into the real belt network —
 * both through the REAL {@code BuildingFactory}/{@code World}, not a hand-built bypass. Everything
 * a class outside {@code com.rustorio.domain.building} needed in order to be WRITABLE at all
 * ({@code Building} open — E5-06; {@code TransportNode}/{@code BeltSegment}/{@code
 * SettlesEachTick} public — this card's own finding, see their javadoc) is exercised here, not
 * just declared possible.
 *
 * <p>Both halves now survive a real save/load round trip. The BUILDING half (a) reuses {@link
 * Furnace}, whose {@code state()} already carries an arbitrary governing prototype (E4-03). The
 * TRANSPORT half (b) — {@link ExampleModBelt} — closes what used to be a gap here: it returns a
 * real {@code BeltState} from {@code state()} and overrides {@code prototypeId()} (the codec-based
 * save format this test now goes through), so a foreign transport node's own modded identity
 * survives reload too, not just its state.
 */
class PhaseFiveAcceptanceTest {

    private static BuildingFactory moddedFactory() {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        ExampleMod.registerAll(prototypes);
        prototypes.freeze();
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);
    }

    /**
     * (a) A "steel press" — no {@link BuildingType} of its own, reuses {@link Furnace}'s existing
     * logic with different data (bigger buffer, twice the speed) — ticks, renders, demolishes, and
     * survives a real save/load round trip, all through the actual {@code BuildingFactory}/{@code
     * World}, not a bypass.
     */
    @Test
    void moddedBuildingWithNoArchetypeOfItsOwnTicksRendersDemolishesAndSurvivesSaveLoad(@TempDir Path dir) {
        BuildingFactory factory = moddedFactory();
        World world = new World(4, 4, factory);

        Building press = factory.create(ExampleMod.STEEL_PRESS_ID, Direction.RIGHT);
        world.restoreBuilding(0, 0, press);
        world.restoreBuilding(1, 0, new Chest());

        // Renders: a real Appearance, not a crash — headless, so this is as far as "рисуются" goes
        // (no GL context here — AGENTS.md's own trap #4).
        assertNotNull(press.appearance());
        assertEquals(BuildingType.PRESS, press.type());

        // Ticks: feeds the vanilla PRESS recipe (IRON_PLATE -> GEAR); steel press's own
        // speedMultiplier (2) must actually drive the REAL World's tick loop, not just a
        // hand-called Furnace.tick().
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));
        int gearTime = RecipeBook.standard().findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();
        int fastTime = Math.max(1, gearTime / 2);
        for (int i = 0; i < fastTime; i++) {
            world.tick();
        }
        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, chest.amount(VanillaItems.GEAR),
                "steel press's own speedMultiplier must actually drive the real World's tick loop");

        // Demolishes: an ordinary building as far as World is concerned — no special case needed.
        assertTrue(world.removeBuilding(0, 0).isPresent());
        assertTrue(world.peek(0, 0).isEmpty());

        // Saves/loads: rebuilt, saved, and reloaded through a FRESH World/factory pair sharing the
        // same modded registry (a real mod's own runtime registers the same content on every
        // launch) — prototype identity, and the tuning it drives, must survive intact.
        world.restoreBuilding(0, 0, press);
        SaveRepository repository = new JsonSaveRepository(dir.resolve("save.json"));
        assertTrue(repository.save(world).succeeded());

        World reloaded = new World(4, 4, moddedFactory());
        assertTrue(repository.load(reloaded).succeeded());
        Furnace reloadedPress = assertInstanceOf(Furnace.class, reloaded.peek(0, 0).orElseThrow());
        assertEquals(ExampleMod.STEEL_PRESS_ID, reloadedPress.prototypeId(),
                "reload must remember the exact modded prototype, not fall back to a vanilla default");
    }

    /**
     * (b) A transport node written from scratch outside {@code com.rustorio.domain.building},
     * placed through the real {@code World}/{@code BuildingFactory}, fuses into the SAME {@code
     * BeltSegment} as a real vanilla {@code Belt} and carries cargo across the boundary between
     * them — the phase's own stated criterion, word for word.
     */
    @Test
    void moddedTransportNodeFusesIntoTheSameSegmentAsAVanillaBeltAndCarriesCargoAcrossIt() {
        BuildingFactory factory = moddedFactory();
        World world = new World(6, 4, factory);

        assertTrue(world.place(BuildingType.CHEST, 0, 0, Direction.RIGHT));
        assertTrue(world.place(BuildingType.BELT, 1, 0, Direction.RIGHT));
        world.restoreBuilding(2, 0, factory.create(ExampleMod.CONVEYOR_ID, Direction.RIGHT));
        assertTrue(world.place(BuildingType.BELT, 3, 0, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 4, 0, Direction.RIGHT));

        Building vanillaBeforeNode = world.peek(1, 0).orElseThrow();
        Building moddedNode = world.peek(2, 0).orElseThrow();
        Building vanillaAfterNode = world.peek(3, 0).orElseThrow();
        assertInstanceOf(TransportNode.class, moddedNode, "must actually implement the capability, not just sit in the map");
        assertSame(((TransportNode) vanillaBeforeNode).segment(), ((TransportNode) moddedNode).segment(),
                "the modded node must be fused into the SAME segment as the vanilla belt behind it, not a separate one");
        assertSame(((TransportNode) moddedNode).segment(), ((TransportNode) vanillaAfterNode).segment(),
                "and the same segment as the vanilla belt ahead of it too — one continuous run, not three");

        Chest source = (Chest) world.peek(0, 0).orElseThrow();
        assertTrue(source.accept(world, VanillaItems.IRON_ORE));

        for (int i = 0; i < 10; i++) {
            world.tick();
        }

        Chest sink = (Chest) world.peek(4, 0).orElseThrow();
        assertEquals(1, sink.count(), "cargo must cross a real vanilla belt, then the modded node, then another real vanilla belt");

        // Demolishes: removing the modded tile must shrink/split the segment correctly, exactly
        // like removing a vanilla belt tile would — proven by the two vanilla halves ending up in
        // DIFFERENT segments afterward, not still fused through a hole.
        assertTrue(world.removeBuilding(2, 0).isPresent());
        assertNotSame(((TransportNode) world.peek(1, 0).orElseThrow()).segment(),
                ((TransportNode) world.peek(3, 0).orElseThrow()).segment(),
                "demolishing the modded tile must split the run, same as demolishing a vanilla belt tile would");
    }

    /**
     * (b, continued) The gap the class javadoc used to describe as honestly unproven — a foreign
     * {@link TransportNode} used to have no way to name its OWN prototype across a save (it
     * borrows {@code BuildingType.BELT} just for {@link Building#type()}), so a naive reload would
     * silently reconstruct a vanilla {@code Belt} instead. {@link ExampleModBelt#prototypeId()}
     * closes that: reload must reconstruct the exact modded class, at the exact modded id, cargo
     * and all.
     */
    @Test
    void moddedTransportNodeSurvivesSaveLoadKeepingItsModdedIdentityAndCargo(@TempDir Path dir) {
        BuildingFactory factory = moddedFactory();
        World world = new World(4, 4, factory);

        world.restoreBuilding(0, 0, factory.create(ExampleMod.CONVEYOR_ID, Direction.RIGHT));
        Building node = world.peek(0, 0).orElseThrow();
        assertTrue(node.accept(world, VanillaItems.IRON_ORE));

        SaveRepository repository = new JsonSaveRepository(dir.resolve("conveyor-save.json"));
        assertTrue(repository.save(world).succeeded());

        World reloaded = new World(4, 4, moddedFactory());
        assertTrue(repository.load(reloaded).succeeded());

        Building reloadedNode = reloaded.peek(0, 0).orElseThrow();
        assertInstanceOf(ExampleModBelt.class, reloadedNode,
                "reload must reconstruct the exact modded class, not fall back to a vanilla Belt");
        assertEquals(ExampleMod.CONVEYOR_ID, reloadedNode.prototypeId(),
                "reload must remember the exact modded prototype, not the borrowed vanilla BELT kind");
        assertEquals(Optional.of(VanillaItems.IRON_ORE), reloadedNode.heldItem(),
                "cargo in transit must survive the round trip too");
    }
}
