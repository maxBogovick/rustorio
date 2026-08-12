package com.rustorio.domain.world;

import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.AffordabilityContext;

/**
 * Read-only face of {@link PlayerInventory} — {@code World.inventory()} returns this, not {@code
 * PlayerInventory} itself, the same discipline already applied to {@link ProductionStatsView} and
 * {@code ResearchView}: a HUD panel holding this can read what the player has, but has no way to
 * grant or spend resources just because it holds a reference.
 */
public interface PlayerInventoryView extends AffordabilityContext {

    /** How many of {@code item} the player currently has — {@code 0} if none. */
    int amount(ItemType item);

    /** Immutable point-in-time snapshot for persistence (Memento pattern) — see {@code JsonSaveRepository} (D-07, DEV_TASKS.md). */
    PlayerInventory.Snapshot snapshot();
}
