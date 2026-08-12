package com.rustorio.domain.building;

import com.rustorio.domain.AffordabilityContext;

/** Whether the player currently has enough of a prototype's {@link BuildingCost} item to place it. */
public final class BuildingAffordability {

    private BuildingAffordability() {
    }

    public static boolean canAfford(BuildingPrototype prototype, AffordabilityContext inventory) {
        BuildingCost cost = prototype.cost();
        return inventory.amount(cost.item()) >= cost.amount();
    }
}
