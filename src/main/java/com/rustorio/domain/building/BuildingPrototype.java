package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;

/**
 * A building's data — everything about it that doesn't depend on which Java class implements its
 * behavior: what it costs, where it may be placed, which sprite draws it. Addressed by {@link
 * ContentId} instead of being read off a fixed {@link com.rustorio.domain.BuildingType} constant
 * via a {@code switch} — a new building variant (a faster, pricier furnace) is a new registered
 * value here, not a new {@code case} in {@code BuildingCost}/{@code PlacementRule}/{@code Textures}.
 *
 * <p>Deliberately does NOT carry footprint or archetype-specific tuning (a furnace's buffer size,
 * a miner's mine time): the phase's own acceptance criterion doesn't require either yet, and
 * {@code BuildingFactory.create/restore}'s dispatch (which Java class a {@link
 * com.rustorio.domain.BuildingType} builds) stays a closed {@code switch} until behavior itself
 * opens up — adding fields nothing reads yet would be exactly the "abstraction for a future that
 * isn't this card's job" the project's own design checklist warns against.
 */
public record BuildingPrototype(ContentId id, BuildingCost cost, PlacementRule placementRule, ContentId texture) {
}
