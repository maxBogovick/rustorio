package com.rustorio.api.mod;

import com.rustorio.api.content.ContentId;

/**
 * Published when a building is actually placed by a player action — not when one is restored while
 * loading a save (loading isn't "placing"; a mod that wants to react to a building EXISTING,
 * regardless of how, should use its own logic inside the building itself, not this event).
 *
 * <p>Carries the prototype id and anchor coordinates only — not the {@code Building} instance or
 * {@code World} itself, same narrow-port reasoning as {@code TickContext}.
 */
public record BuildingPlacedEvent(ContentId prototypeId, int x, int y) {
}
