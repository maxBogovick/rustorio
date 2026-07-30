package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;

/**
 * A building's data — everything about it that doesn't depend on which Java class implements its
 * behavior: what it costs, where it may be placed, which sprite draws it. Addressed by {@link
 * ContentId} instead of being read off a fixed {@link com.rustorio.domain.BuildingType} constant
 * via a {@code switch} — a new building variant (a faster, pricier furnace) is a new registered
 * value here, not a new {@code case} in {@code BuildingCost}/{@code PlacementRule}/{@code Textures}.
 *
 * <p>{@code bufferMax}/{@code speedMultiplier} are {@link Furnace}-specific tuning — every OTHER
 * archetype ignores them (registered as {@code 0}/{@code 1}, see {@link VanillaBuildings}).
 * Deliberately not a general per-archetype parameter bag: only one archetype needs tuning today,
 * and {@code BuildingFactory.create/restore}'s dispatch (which Java class a {@link
 * com.rustorio.domain.BuildingType} builds) stays closed until behavior itself opens up — a
 * generic mechanism for parameters nothing else reads yet would be exactly the "abstraction for a
 * future that isn't this card's job" the project's own design checklist warns against.
 *
 * <p>Still does NOT carry footprint: the phase's own acceptance criterion doesn't require it.
 *
 * <p>{@code acceptsSpeedEffects} replaces {@code UpgradeSpeedAction}'s old {@code instanceof}
 * chain over six single-slot/segment-joining classes: whether a kind's second {@code tick()} call
 * (from a speed wrapper) does anything meaningful is a property of the kind, not something the
 * upgrade action should determine by checking concrete Java types.
 */
public record BuildingPrototype(ContentId id, BuildingCost cost, PlacementRule placementRule, ContentId texture,
        int bufferMax, int speedMultiplier, boolean acceptsSpeedEffects) {
}
