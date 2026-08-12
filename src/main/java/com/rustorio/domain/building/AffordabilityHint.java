package com.rustorio.domain.building;

import com.rustorio.domain.AffordabilityContext;
import java.util.Optional;

/** Human-readable text when a building's cost exceeds the player's inventory. */
public final class AffordabilityHint {

    private AffordabilityHint() {
    }

    public static Optional<String> needMoreMessage(BuildingPrototype prototype, AffordabilityContext inventory,
            String locale) {
        String normalized = locale == null ? "en" : locale.toLowerCase(java.util.Locale.ROOT);
        BuildingCost cost = prototype.cost();
        int have = inventory.amount(cost.item());
        if (have >= cost.amount()) {
            return Optional.empty();
        }
        String itemName = cost.item().label();
        if ("ru".equals(normalized)) {
            return Optional.of("Не хватает — " + cost.amount() + " " + itemName + " (есть " + have + ")");
        }
        return Optional.of("Need — " + cost.amount() + " " + itemName + " (have " + have + ")");
    }
}
