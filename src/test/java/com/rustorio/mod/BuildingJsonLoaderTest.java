package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.model.TechType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TraitKey;
import com.rustorio.domain.building.Traits;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioral proof for {@link BuildingJsonLoader} — a JSON-configured prototype must actually
 * behave differently at the {@link Furnace#tick} level, not just carry the right numbers, the same
 * standard {@code BuildingFactoryTest.createBuildsAPrototypeWithNoCorrespondingBuildingType} already
 * holds a Java-literal modded prototype to.
 */
class BuildingJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void jsonConfiguredBuildingReusesTheNamedArchetypeAndAppliesItsOwnTuning() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("steel_press.json", """
                { "path": "steel_press", "label": "Steel Press", "archetype": "PRESS", "kind": "PRESS",
                  "cost": { "item": "rustorio:iron_plate", "amount": 20 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold",
                  "bufferMax": 10, "speedMultiplier": 2, "acceptsSpeedEffects": true }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        ContentId id = ContentId.of("testmod:steel_press");
        BuildingPrototype prototype = context.buildings().peek(id).orElseThrow();
        assertEquals(10, prototype.bufferMax());
        assertEquals(2, prototype.speedMultiplier());
        assertEquals(20, prototype.cost().amount());

        context.items().freeze();
        Registry<BuildingPrototype> buildings = new Registry<>();
        VanillaBuildings.registerAll(buildings);
        buildings.register(id, prototype);
        buildings.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), context.items(), buildings);

        Building built = factory.create(id, Direction.RIGHT);
        Furnace press = assertInstanceOf(Furnace.class, built);

        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));

        int gearTime = RecipeBook.standard().findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();
        int fastTime = Math.max(1, gearTime / 2); // this prototype's own speedMultiplier: 2
        for (int i = 0; i < fastTime - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count(), "must not finish before the JSON prototype's sped-up time");
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.amount(VanillaItems.GEAR),
                "cooked in half the vanilla PRESS's time via the JSON prototype's own speedMultiplier");
    }

    @Test
    void explicitFootprintInJsonDrivesTheRealConstructedBuildingNotJustTheArchetypesOwn() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("big_furnace.json", """
                { "path": "big_furnace", "label": "Big Furnace", "archetype": "FURNACE",
                  "cost": { "item": "rustorio:iron_plate", "amount": 5 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold",
                  "footprintWidth": 3, "footprintHeight": 2, "bufferMax": 5, "speedMultiplier": 1,
                  "acceptsSpeedEffects": true }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        ContentId id = ContentId.of("testmod:big_furnace");
        BuildingPrototype prototype = context.buildings().peek(id).orElseThrow();
        assertEquals(3, prototype.footprintWidth());
        assertEquals(2, prototype.footprintHeight());

        context.items().freeze();
        Registry<BuildingPrototype> buildings = new Registry<>();
        VanillaBuildings.registerAll(buildings);
        buildings.register(id, prototype);
        buildings.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), context.items(), buildings);

        Building built = factory.create(id, Direction.RIGHT);
        assertEquals(3, built.footprintWidth(), "the REAL constructed building must read footprint from ITS OWN prototype, not the borrowed archetype's");
        assertEquals(2, built.footprintHeight());
    }

    @Test
    void unknownArchetypeNamesTheAllowedValues() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "TELEPORTER",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "ALWAYS", "texture": "rustorio:miner" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("MINER"));
    }

    @Test
    void unknownPlacementNamesTheAllowedValues() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "CHEST",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "FLOATING", "texture": "rustorio:chest" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("rustorio:needs_passable_terrain"),
                "the message lists the rules actually registered, by id: " + thrown.getMessage());
    }

    /**
     * The reason placement rules became registered content: a code mod can contribute a genuinely
     * new CONDITION, and any data mod — including one that has never heard of it — can then name it
     * from JSON. Before this, the set of rules a building file could name was a fixed list inside
     * the loader, and "where may this stand" was the one property of a building nobody could extend.
     */
    @Test
    void aBuildingMayNameAPlacementRuleSomeModRegistered() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        // A rule no vanilla building uses: only the top-left quadrant of the map.
        context.placementRules().register(ContentId.of("testmod:northwest_only"),
                (x, y, oreLayout) -> x < 10 && y < 10);
        write("outpost.json", """
                { "path": "outpost", "label": "Outpost", "archetype": "CHEST",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "northwest_only", "texture": "rustorio:chest" }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        BuildingPrototype outpost = context.buildings().peek(ContentId.of("testmod:outpost")).orElseThrow();
        assertTrue(outpost.placementRule().test(1, 1, PatchOreLayout.standard()),
                "the mod's own rule accepts a cell in its quadrant");
        assertFalse(outpost.placementRule().test(50, 50, PatchOreLayout.standard()),
                "and rejects one outside it — the registered rule really is the one being used");
    }

    @Test
    void unresolvedCostItemFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "CHEST",
                  "cost": { "item": "rustorio:unobtainium", "amount": 1 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:chest" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("rustorio:unobtainium"));
    }

    @Test
    void missingCostObjectFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "CHEST", "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:chest" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("cost"));
    }

    /**
     * The fluid ports are what make a JSON-authored pump a pump, so a misspelled fluid has to be as
     * loud as a misspelled cost item already is — otherwise the machine loads and silently pumps
     * nothing, which is the hardest kind of mod bug to find.
     */
    @Test
    void unknownFluidInAPortFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("derrick.json", """
                { "path": "derrick", "label": "Derrick", "archetype": "PUMP",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "ADJACENT_TO_WATER", "texture": "rustorio:pump",
                  "fluidOutput": "rustorio:unobtainium" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("rustorio:unobtainium"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("fluidOutput"), "the message has to name the field: " + thrown.getMessage());
    }

    /**
     * A bare fluid reference resolves in the CURRENT mod's namespace, exactly as a bare cost item
     * does. Pinned because it is the trap the fluid documentation itself fell into: {@code "water"} in a
     * mod's own file means that mod's water, not vanilla's, and the resulting error has to say so.
     */
    @Test
    void aBareFluidReferenceResolvesInTheOwningModsNamespace() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("derrick.json", """
                { "path": "derrick", "label": "Derrick", "archetype": "PUMP",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "ADJACENT_TO_WATER", "texture": "rustorio:pump",
                  "fluidOutput": "water" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("testmod:water"),
                "a bare name is the mod's own, and the message must show which id it actually looked for: "
                        + thrown.getMessage());
    }

    @Test
    void aPowerBlockThatIsNotAnObjectNamesTheShapeItWanted() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "POLE",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:pole",
                  "power": 5 }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("power"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("radius"), "the message shows the shape it wanted: " + thrown.getMessage());
    }

    /**
     * A chest never draws power — putting a {@code "power"} block on one would load and then do
     * nothing. Recipe machines ({@code ASSEMBLER}/{@code FURNACE}/{@code PRESS}) do honor demand
     * now; this rejection is for archetypes that still never call {@code drawPower}.
     */
    @Test
    void aPowerBlockOnAnArchetypeThatNeverReadsItIsRejected() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("powered_crate.json", """
                { "path": "powered_crate", "label": "Powered Crate", "archetype": "CHEST",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:chest",
                  "power": { "demand": 30 } }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("CHEST"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("MINER") || thrown.getMessage().contains("ASSEMBLER"),
                "names an archetype that DOES honor power, so the modder knows what would actually work: "
                        + thrown.getMessage());
    }

    /** An ASSEMBLER with demand is legal now — Furnace.tick draws power when the prototype asks. */
    @Test
    void aPowerBlockOnAnAssemblerIsAcceptedAndStored() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("robo_assembler.json", """
                { "path": "robo_assembler", "label": "Robo-Assembler", "archetype": "ASSEMBLER",
                  "cost": { "item": "rustorio:gear", "amount": 15 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:assembler",
                  "power": { "demand": 30 } }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        BuildingPrototype robo = context.buildings().peek(ContentId.of("testmod:robo_assembler")).orElseThrow();
        PowerSpec power = robo.power();
        assertTrue(power != null, "ASSEMBLER may carry a power block now");
        assertEquals(30, power.demand());
    }

    /** A negative demand would be a machine that GENERATES by asking for power — rejected at the door, not left to the tick to puzzle over. */
    @Test
    void aNegativePowerValueNamesThePathToTheOffendingNumber() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird", "archetype": "ELECTRIC_MINER",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "NEEDS_ORE", "texture": "rustorio:electric_miner",
                  "power": { "demand": -10 } }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("power.demand"), thrown.getMessage());
    }

    /**
     * Absent {@code power} must stay absent, not become a zeroed spec: "this building has nothing to
     * do with electricity" and "it is electrical and asks for nothing" are different claims, and the
     * renderer already tells them apart by drawing an accent stripe for the second.
     */
    @Test
    void aBuildingWithoutAPowerBlockDeclaresNoPowerSpecAtAll() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("crate.json", """
                { "path": "crate", "label": "Crate", "archetype": "CHEST",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:chest" }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        BuildingPrototype crate = context.buildings().peek(ContentId.of("testmod:crate")).orElseThrow();
        assertEquals(null, crate.power(), "an ordinary building is not electrical, not electrical-asking-for-zero");
        assertEquals(null, crate.fluidInput(), "and touches no fluid either");
        assertEquals(null, crate.fluidOutput());
    }

    /**
     * The typo that used to be invisible from both ends: a misspelled field is read by nobody, so
     * the building loads and simply does not work — a pump that pumps nothing, with no error
     * anywhere to explain it. Naming the offending key is the difference between a five-minute fix
     * and an evening of staring at a machine that looks fine.
     */
    @Test
    void aMisspelledFieldNameIsRejectedRatherThanSilentlyIgnored() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("derrick.json", """
                { "path": "derrick", "label": "Derrick", "archetype": "PUMP",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "ADJACENT_TO_WATER", "texture": "rustorio:pump",
                  "fluidOutut": "rustorio:water" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("fluidOutut"), "the message names the key as written: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("fluidOutput"), "and lists the one that was meant: " + thrown.getMessage());
    }

    /** The same protection one level down, where the published schema never reached — {@code ContentSchemaTest} only compares top-level keys. */
    @Test
    void aMisspelledFieldInsideThePowerBlockIsRejectedToo() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("drill.json", """
                { "path": "drill", "label": "Drill", "archetype": "ELECTRIC_MINER",
                  "cost": { "item": "rustorio:iron_plate", "amount": 1 },
                  "placement": "NEEDS_ORE", "texture": "rustorio:electric_miner",
                  "power": { "demmand": 10 } }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> BuildingJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("demmand"), thrown.getMessage());
    }

    /**
     * The point of traits, stated as a test: a mod declares a property the engine has never heard
     * of, hangs it on a prototype, and reads it back — with nothing in {@code BuildingPrototype},
     * {@code VanillaBuildings}, this loader or the parity test knowing it exists.
     *
     * <p>Before traits, the same thing cost a component on the prototype record, a rung on two
     * telescopes of constructors, a field here and a line in the parity test. That was the whole
     * complaint this refactor answers, so the proof belongs in a test rather than in a javadoc.
     */
    @Test
    void aModCanHangItsOwnPropertyOnAPrototypeWithoutTouchingTheEngine() {
        TraitKey<Integer> noiseLevel =
                new TraitKey<>(ContentId.of("testmod:noise"), "noise", Integer.class);

        BuildingPrototype quiet = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.CHEST));
        BuildingPrototype loud = new BuildingPrototype(ContentId.of("testmod:loud_chest"), "Loud Chest",
                quiet.cost(), quiet.placementRule(), quiet.texture(), 1, 1, quiet.bufferMax(),
                quiet.speedMultiplier(), quiet.acceptsSpeedEffects(), quiet.behavior(), quiet.restoreBehavior(),
                quiet.codec(), quiet.recipeKind(), quiet.fuelItem(), Traits.one(noiseLevel, 11));

        assertEquals(11, loud.traits().get(noiseLevel).orElseThrow(),
                "a mod's own trait comes back typed, with no cast and no engine change");
        assertTrue(quiet.traits().get(noiseLevel).isEmpty(),
                "and a building that never declared it says so, rather than answering some default");
        assertEquals(null, loud.power(),
                "declaring one trait does not accidentally declare the others");
    }

    /**
     * A JSON building that omits {@code "speedTech"} must not lose the vanilla speed bonus its
     * borrowed archetype has always had — silently dropping it would be a behavior change for
     * every furnace-like JSON building ever written, none of which mention this new field.
     */
    @Test
    void omittingSpeedTechInheritsTheBorrowedArchetypesOwnDefault() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("steel_press.json", """
                { "path": "steel_press", "label": "Steel Press", "archetype": "PRESS",
                  "cost": { "item": "rustorio:iron_plate", "amount": 20 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold" }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        BuildingPrototype prototype = context.buildings().peek(ContentId.of("testmod:steel_press")).orElseThrow();
        assertEquals(VanillaTechs.FAST_SMELTING, prototype.speedTech(),
                "PRESS is a Furnace archetype: its vanilla default gate is FAST_SMELTING, same as before this field existed");
    }

    /**
     * The capability {@code VanillaTechs}'s own javadoc used to say a JSON-only mod could not have:
     * a building on a shared archetype (PRESS, here) sped up by a technology THIS mod defines,
     * proven end to end — not just that the trait carries the right {@link ContentId}, but that the
     * real {@link Furnace} instance actually cooks in half the time once that tech is unlocked.
     */
    @Test
    void aJsonBuildingCanNameItsOwnSpeedTechInsteadOfTheArchetypesDefault() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("turbo_press.json", """
                { "path": "turbo_press", "label": "Turbo Press", "archetype": "PRESS", "kind": "PRESS",
                  "cost": { "item": "rustorio:iron_plate", "amount": 20 },
                  "placement": "NEEDS_PASSABLE_TERRAIN", "texture": "rustorio:furnace_cold",
                  "bufferMax": 5, "speedMultiplier": 1, "acceptsSpeedEffects": true,
                  "speedTech": "overclock" }
                """);

        BuildingJsonLoader.loadInto(tempDir, modId, context);

        ContentId id = ContentId.of("testmod:turbo_press");
        BuildingPrototype prototype = context.buildings().peek(id).orElseThrow();
        ContentId overclock = ContentId.of("testmod:overclock");
        assertEquals(overclock, prototype.speedTech(),
                "a bare speedTech resolves in the declaring mod's own namespace, like every other reference here");

        context.items().freeze();
        Registry<BuildingPrototype> buildings = new Registry<>();
        VanillaBuildings.registerAll(buildings);
        buildings.register(id, prototype);
        buildings.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), context.items(), buildings);

        Registry<TechType> techs = new Registry<>();
        techs.register(overclock, new TechType(overclock, "Overclock", 10));
        techs.freeze();
        World world = new World(4, 4, factory, techs);
        world.addResearchPoints(10);
        assertTrue(world.tryUnlockTech(overclock), "the mod's own tech, not a vanilla one, must be unlockable and readable");

        Building built = factory.create(id, Direction.RIGHT);
        Furnace press = assertInstanceOf(Furnace.class, built);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));

        int gearTime = RecipeBook.standard().findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();
        int fastTime = Math.max(1, gearTime / 2);
        for (int i = 0; i < fastTime - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count(), "must not finish before the mod's own tech-adjusted time");
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.amount(VanillaItems.GEAR),
                "cooked in half time via the mod's OWN technology, not FAST_SMELTING — the engine never registered that one here");
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
