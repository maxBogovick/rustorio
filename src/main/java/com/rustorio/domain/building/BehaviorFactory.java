package com.rustorio.domain.building;

import com.rustorio.domain.Direction;

/**
 * How a {@link BuildingPrototype} turns into a BRAND-NEW live {@link Building} — the data half of
 * what used to be {@code BuildingFactory.create}'s own hardcoded {@code switch} over {@code
 * BuildingType} (which Java class a kind builds). Registered once per prototype (see {@link
 * VanillaBuildings#registerAll}), not written fresh per building placed — {@code
 * BuildingFactory.create} no longer contains a single {@code new Miner(...)}/{@code new
 * Chest(...)} call anywhere; every one of them lives in a behavior lambda instead, next to the
 * prototype it belongs to. See {@link RestoreFactory} for the memento-restoring counterpart.
 *
 * <p>A brand-new prototype (one with no corresponding {@link com.rustorio.domain.BuildingType} at
 * all) is buildable through {@code BuildingFactory.create(ContentId, Direction)} as long as it
 * registers a behavior here — that is the whole point: adding a building archetype backed by an
 * EXISTING Java class (a bigger/faster furnace, say) no longer needs a new {@code case} anywhere.
 * A genuinely new Java class (new tick logic) still needs one written and referenced from a
 * behavior lambda — this interface doesn't remove that need, only the {@code switch} that used to
 * gate which existing class a given kind was allowed to resolve to.
 */
@FunctionalInterface
public interface BehaviorFactory {

    /** Build a brand-new instance of {@code self}'s archetype, facing {@code direction} where that matters. */
    Building create(BuildingPrototype self, Direction direction, BuildingFactory factory);
}
