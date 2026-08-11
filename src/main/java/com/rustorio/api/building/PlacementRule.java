package com.rustorio.api.building;

/**
 * Facade for {@link com.rustorio.domain.building.PlacementRule}. The four vanilla constants
 * ({@link #NEEDS_PASSABLE_TERRAIN}, …) are inherited from the domain interface; new mods may also
 * use bare names through {@link com.rustorio.api.dsl.BuildingDsl#placement(String)}.
 */
public interface PlacementRule extends com.rustorio.domain.building.PlacementRule {
}
