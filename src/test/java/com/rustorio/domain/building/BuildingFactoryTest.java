package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link BuildingFactory}: the single dispatch point from {@link BuildingType} to a concrete
 * {@link Building}, for both fresh construction and memento-based restoration.
 */
class BuildingFactoryTest {

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

        assertEquals(BuildingType.ASSEMBLER, assembler.type());
        assertEquals(2, assembler.footprintWidth());
        assertEquals(2, assembler.footprintHeight());
    }

    @Test
    void createdFurnaceMatchesRequestedKind() {
        assertEquals(BuildingType.FURNACE, factory.create(BuildingType.FURNACE, Direction.RIGHT).type());
        assertEquals(BuildingType.PRESS, factory.create(BuildingType.PRESS, Direction.RIGHT).type());
    }

    @Test
    void restoreRebuildsFromAMementoWithNoSpeedModuleWhenLevelIsZero() {
        Building original = factory.create(BuildingType.BELT, Direction.UP);
        Building restored = factory.restore(original.memento(), 0);

        assertInstanceOf(Belt.class, restored);
        assertEquals(0, restored.speedLevel());
    }

    @Test
    void restoreReappliesTheExactNumberOfSpeedModuleLayers() {
        Building original = factory.create(BuildingType.FURNACE, Direction.DOWN);
        Building restored = factory.restore(original.memento(), 2);

        assertInstanceOf(SpeedModule.class, restored);
        assertEquals(2, restored.speedLevel());
        assertEquals(BuildingType.FURNACE, restored.type()); // delegates through both layers
    }

    /** (X-01, DEV_TASKS.md) Splitter/Filter/Inserter through the actual factory door, not just their own direct constructors. */
    @Test
    void restoreRoundTripsSplitterFilterAndInserterThroughTheFactory() {
        Building splitter = factory.restore(factory.create(BuildingType.SPLITTER, Direction.RIGHT).memento(), 0);
        assertInstanceOf(Splitter.class, splitter);

        Building filter = factory.restore(factory.create(BuildingType.FILTER, Direction.RIGHT).memento(), 0);
        assertInstanceOf(Filter.class, filter);
        assertEquals(Item.IRON_ORE, ((Filter) filter).filterItem());

        Building inserter = factory.restore(factory.create(BuildingType.INSERTER, Direction.RIGHT).memento(), 0);
        assertInstanceOf(Inserter.class, inserter);
    }
}
