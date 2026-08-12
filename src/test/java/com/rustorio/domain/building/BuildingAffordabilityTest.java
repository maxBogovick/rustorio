package com.rustorio.domain.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

class BuildingAffordabilityTest {

    @Test
    void affordableWhenInventoryCoversTheCost() {
        BuildingPrototype chest = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.CHEST));
        World world = new World(4, 4);

        assertTrue(BuildingAffordability.canAfford(chest, world.inventory()));
    }

    @Test
    void unaffordableWhenInventoryIsShort() {
        BuildingPrototype lab = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.LAB));
        World world = new World(4, 4);

        assertFalse(BuildingAffordability.canAfford(lab, world.inventory()));
        world.creditItem(VanillaItems.GEAR, lab.cost().amount());
        assertTrue(BuildingAffordability.canAfford(lab, world.inventory()));
    }
}
