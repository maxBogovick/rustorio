package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
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
        assertInstanceOf(UndergroundBelt.class, factory.create(BuildingType.UNDERGROUND_IN, Direction.RIGHT));
        assertInstanceOf(UndergroundBelt.class, factory.create(BuildingType.UNDERGROUND_OUT, Direction.RIGHT));
        assertInstanceOf(Lab.class, factory.create(BuildingType.LAB, Direction.RIGHT));
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
}
