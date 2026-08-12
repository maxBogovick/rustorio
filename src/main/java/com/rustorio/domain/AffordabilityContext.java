package com.rustorio.domain;

import com.rustorio.api.content.model.ItemType;

/**
 * Read-only inventory counts for {@link com.rustorio.domain.building.BuildingAffordability} —
 * lives in the inner domain ring so building code never imports {@code domain.world}.
 */
public interface AffordabilityContext {

    int amount(ItemType item);
}
