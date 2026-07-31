package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;

/**
 * Observer pattern: notified when a building is actually placed via {@link World#place}. Not
 * called by {@link World#restoreBuilding} — restoring a save or an undo/redo step isn't a player
 * "placing" anything, the same distinction {@code com.rustorio.api.mod.BuildingPlacedEvent}'s own
 * javadoc draws for the mod-facing event this feeds.
 */
@FunctionalInterface
public interface BuildingPlacedListener {
    void onBuildingPlaced(ContentId prototypeId, int x, int y);
}
