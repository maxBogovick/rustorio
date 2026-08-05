package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;

/**
 * A veto consulted before a building is actually placed — the domain-side half of a cancellable
 * mod event, the same way {@link BuildingPlacedListener} is the domain-side half of a notification.
 * The domain never imports {@code com.rustorio.api.mod}; {@code com.rustorio.mod.EventWiring}
 * translates between the two vocabularies.
 *
 * <p>Deliberately consulted only on a player action, never inside a tick. A veto is arbitrary
 * third-party code, and this project's tick budget forbids calling into one per belt per step; a
 * placement happens when a person clicks, so the same code costs nothing measurable there.
 *
 * <p>Consulted AFTER the placement is known to be legal (in bounds, cell free, terrain suitable)
 * and BEFORE anything is committed — so a veto never has to re-implement the engine's own rules to
 * decide, and a refusal leaves the world exactly as it was.
 */
@FunctionalInterface
public interface PlacementVeto {

    /** @return {@code false} to refuse this placement; the player's action then does nothing at all. */
    boolean allowPlacement(ContentId prototypeId, int x, int y);
}
