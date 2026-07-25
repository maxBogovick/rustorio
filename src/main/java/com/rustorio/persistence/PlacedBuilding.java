package com.rustorio.persistence;

import com.rustorio.domain.building.BuildingMemento;

/**
 * One building's position, upgrade depth and captured state — a save file's building row. Kind
 * lives inside {@code state} itself (every {@link BuildingMemento} variant carries everything
 * needed to rebuild its exact building, see {@link BuildingMemento.FurnaceState#kind()}), so it
 * isn't repeated here as a second source of truth.
 */
record PlacedBuilding(int x, int y, int speedLevel, BuildingMemento state) {
}
