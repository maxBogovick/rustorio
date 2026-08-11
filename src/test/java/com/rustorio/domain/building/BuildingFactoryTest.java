package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.vanilla.VanillaSprites;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BuildingFactory}: the single dispatch point from {@link BuildingType} to a concrete
 * {@link Building}, for both fresh construction and codec-based restoration.
 */
class BuildingFactoryTest {

    private static final ContentId STEEL_PRESS_ID = ContentId.of("examplemod:steel_press");

    private final BuildingFactory factory = BuildingFactory.standard();

    @Test
    void createsTheRightConcreteClassForEveryBuildingType() {
        assertInstanceOf(Miner.class, factory.create(BuildingType.MINER, Direction.RIGHT));
        assertInstanceOf(Chest.class, factory.create(BuildingType.CHEST, Direction.RIGHT));
        assertInstanceOf(Furnace.class, factory.create(BuildingType.FURNACE, Direction.RIGHT));
        assertInstanceOf(Furnace.class, factory.create(BuildingType.PRESS, Direction.RIGHT));
        assertInstanceOf(Belt.class, factory.create(BuildingType.BELT, Direction.RIGHT));
        assertInstanceOf(Splitter.class, factory.create(BuildingType.SPLITTER, Direction.RIGHT));
        assertInstanceOf(Filter.class, factory.create(BuildingType.FILTER, Direction.RIGHT));
        assertInstanceOf(Inserter.class, factory.create(BuildingType.INSERTER, Direction.RIGHT));
        assertInstanceOf(UndergroundBelt.class, factory.create(BuildingType.UNDERGROUND_IN, Direction.RIGHT));
        assertInstanceOf(UndergroundBelt.class, factory.create(BuildingType.UNDERGROUND_OUT, Direction.RIGHT));
        assertInstanceOf(Lab.class, factory.create(BuildingType.LAB, Direction.RIGHT));
        assertInstanceOf(Furnace.class, factory.create(BuildingType.ASSEMBLER, Direction.RIGHT));
    }

    /** (X-03, DEV_TASKS.md) {@code ASSEMBLER} reuses {@link Furnace} outright — see {@link BuildingFactory#create}'s own comment on why. */
    @Test
    void assemblerIsA2x2FurnaceOfItsOwnKind() {
        Building assembler = factory.create(BuildingType.ASSEMBLER, Direction.RIGHT);

        assertEquals(VanillaBuildings.idFor(BuildingType.ASSEMBLER), assembler.prototypeId());
        assertEquals(2, assembler.footprintWidth());
        assertEquals(2, assembler.footprintHeight());
    }

    @Test
    void createdFurnaceMatchesRequestedKind() {
        assertEquals(VanillaBuildings.idFor(BuildingType.FURNACE), factory.create(BuildingType.FURNACE, Direction.RIGHT).prototypeId());
        assertEquals(VanillaBuildings.idFor(BuildingType.PRESS), factory.create(BuildingType.PRESS, Direction.RIGHT).prototypeId());
    }

    /** A belt never tracks a speedLevel (not eligible — see {@code BuildingPrototype#acceptsSpeedEffects}), so a restore round trip must not manufacture one out of nowhere. */
    @Test
    void restoreIgnoresSpeedLevelForAKindThatDoesNotTrackIt() {
        Building original = factory.create(BuildingType.BELT, Direction.UP);
        Building restored = roundTrip(factory, original);

        assertInstanceOf(Belt.class, restored);
        assertEquals(0, restored.speedLevel());
    }

    /** A furnace DOES track speedLevel (E5-05) — a restore round trip must carry it straight through the archetype's own state record, no wrapper class involved. */
    @Test
    void restoreThreadsSpeedLevelDirectlyIntoAnEligibleArchetype() {
        Building original = factory.create(BuildingType.FURNACE, Direction.DOWN).withSpeedLevel(2);
        Building restored = roundTrip(factory, original);

        assertInstanceOf(Furnace.class, restored);
        assertEquals(2, restored.speedLevel());
        assertEquals(VanillaBuildings.idFor(BuildingType.FURNACE), restored.prototypeId());
    }

    /** (X-01, DEV_TASKS.md) Splitter/Filter/Inserter through the actual factory door, not just their own direct constructors. */
    @Test
    void restoreRoundTripsSplitterFilterAndInserterThroughTheFactory() {
        Building splitter = roundTrip(factory, factory.create(BuildingType.SPLITTER, Direction.RIGHT));
        assertInstanceOf(Splitter.class, splitter);

        Building filter = roundTrip(factory, factory.create(BuildingType.FILTER, Direction.RIGHT));
        assertInstanceOf(Filter.class, filter);
        assertEquals(VanillaItems.IRON_ORE, ((Filter) filter).filterItem());

        Building inserter = roundTrip(factory, factory.create(BuildingType.INSERTER, Direction.RIGHT));
        assertInstanceOf(Inserter.class, inserter);
    }

    /**
     * (E5-07) A prototype with NO corresponding {@link BuildingType} at all, built through the
     * REAL {@link BuildingFactory#create(ContentId, Direction)} — not a direct {@code new
     * Furnace(...)} bypass (see {@code ModdedFurnaceAcceptanceTest}'s own note on this exact
     * restriction, which this test proves lifted). Proven by BEHAVIOR, not just concrete class:
     * the modded prototype's own {@code speedMultiplier} (2) must actually drive the built
     * furnace's cooking speed, not silently fall back to any default.
     */
    @Test
    void createBuildsAPrototypeWithNoCorrespondingBuildingType() {
        BuildingFactory moddedFactory = factoryWithSteelPress();

        Building built = moddedFactory.create(STEEL_PRESS_ID, Direction.RIGHT);
        Furnace press = assertInstanceOf(Furnace.class, built);
        assertEquals(STEEL_PRESS_ID, press.prototypeId(),
                "a modded prototype identifies as itself, never as the archetype it reuses");

        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);
        assertTrue(press.accept(world, VanillaItems.IRON_PLATE));

        int gearTime = RecipeBook.standard().findByOutput(BuildingType.PRESS, VanillaItems.GEAR).orElseThrow().time();
        int fastTime = Math.max(1, gearTime / 2); // steel press's own speedMultiplier — see factoryWithSteelPress()
        for (int i = 0; i < fastTime - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count(), "must not finish before the sped-up time");
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.amount(VanillaItems.GEAR),
                "cooked in half the vanilla PRESS's time via the modded prototype's own speedMultiplier"
                        + " — reached through the real factory.create, not a bypass");
    }

    /**
     * (E5-07) {@code restore()} must resolve a modded prototype's OWN registered behavior too, not
     * just {@code create()} — a {@code FurnaceState} naming a prototype absent from any vanilla
     * enum must still restore through it when the SURROUNDING factory's own registry has it.
     */
    @Test
    void restoreResolvesAPrototypeWithNoCorrespondingBuildingType() {
        BuildingFactory moddedFactory = factoryWithSteelPress();
        Building built = moddedFactory.create(STEEL_PRESS_ID, Direction.RIGHT);
        assertEquals(STEEL_PRESS_ID, built.prototypeId());

        Building restored = roundTrip(moddedFactory, built);
        Furnace press = assertInstanceOf(Furnace.class, restored);
        assertEquals(STEEL_PRESS_ID, press.prototypeId(),
                "a modded prototype identifies as itself, never as the archetype it reuses");

        // Buffer 10, not the vanilla PRESS default of 5 — proves restore() resolved the MODDED
        // prototype via its own registered behavior, not a hardcoded case that would silently
        // fall back to the vanilla default the way a stale/naive implementation might.
        World world = new World(4, 4);
        world.restoreBuilding(1, 0, new Chest());
        for (int i = 0; i < 6; i++) {
            assertTrue(press.accept(world, VanillaItems.IRON_PLATE),
                    "buffer 10 must hold more than the vanilla default of 5 — this is the 6th unit");
        }
    }

    /** A "steel press" — bigger buffer, twice the speed — registered next to the 12 vanilla prototypes, under an id no {@link BuildingType} maps to. */
    private static BuildingFactory factoryWithSteelPress() {
        BuildingPrototype steelPress = new BuildingPrototype(
                STEEL_PRESS_ID,
                "Steel Press",
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.FURNACE_COLD,
                1, 1,
                10, // buffer — double the vanilla PRESS's 5
                2, // speed multiplier — twice as fast
                true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(BuildingType.PRESS, state, factory.recipeBook(), self);
                },
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec(),
                // Opts into the shared vanilla PRESS recipe pool (this test feeds IRON_PLATE and
                // expects the real vanilla GEAR recipe) instead of the private-pool default.
                VanillaBuildings.idFor(BuildingType.PRESS), null);
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        prototypes.register(STEEL_PRESS_ID, steelPress);
        prototypes.freeze();
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);
    }

    /**
     * Regression test for code review finding S2: a {@link Filter} built through a factory that
     * was itself constructed with a custom {@link Registry} must cycle through THAT registry —
     * before the fix, {@link Filter#cycleFilterItem} always reached for {@code
     * VanillaItems.frozen()} instead, no matter which registry the surrounding factory actually held.
     */
    @Test
    void filterCreatedByTheFactoryCyclesThroughTheFactorysOwnRegistry() {
        Registry<ItemType> modded = new Registry<>();
        VanillaItems.registerAll(modded);
        ItemType copperOre = new ItemType(ContentId.of("test:copper_ore"), "Copper Ore", false, 0, ItemShape.CIRCLE);
        modded.register(copperOre.id(), copperOre);
        modded.freeze();
        BuildingFactory moddedFactory =
                new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), modded);

        Filter created = (Filter) moddedFactory.create(BuildingType.FILTER, Direction.RIGHT);
        while (created.filterItem() != copperOre) {
            created.cycleFilterItem(); // must terminate: copperOre is registered in `modded`
        }
        assertEquals(copperOre, created.filterItem());

        // restore() must wire in the SAME registry: cycling a restored filter whose filterItem is
        // copperOre would throw NoSuchElementException against VanillaItems.frozen() (which has
        // no idea "test:copper_ore" exists) if restore() fell back to the hardcoded vanilla
        // registry instead of the factory's own — exactly the bug the finding described.
        Filter restored = (Filter) roundTrip(moddedFactory, created);
        assertDoesNotThrow(restored::cycleFilterItem);
    }

    /** Encode-decode-restore round trip through a factory's own registered {@link Codec}, mirroring exactly what {@code JsonSaveRepository} does on save/load. */
    private static Building roundTrip(BuildingFactory factory, Building building) {
        ContentId prototypeId = building.prototypeId();
        BuildingPrototype prototype = factory.prototype(prototypeId);
        Object encoded = prototype.encodeState(building.state());
        return factory.restore(prototypeId, encoded);
    }
}
