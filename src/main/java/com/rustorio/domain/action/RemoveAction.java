package com.rustorio.domain.action;

import com.rustorio.domain.building.Building;
import com.rustorio.domain.world.World;
import org.jspecify.annotations.Nullable;

/** Demolish the building on a cell; undo puts back the very object that was demolished. */
public final class RemoveAction implements PlayerAction {

    private final int x;
    private final int y;

    /**
     * What was demolished — captured in {@link #apply}, restored in {@link #undo}. A plain
     * nullable field, not {@code Optional<Building>}: Effective Java Item 55 says {@code Optional}
     * belongs on method return types, never on a field (see {@code Recipe#input2} for the same
     * reasoning spelled out in full).
     */
    private @Nullable Building removed;

    public RemoveAction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean apply(World world) {
        removed = world.removeBuilding(x, y).orElse(null);
        return removed != null;
    }

    @Override
    public void undo(World world) {
        // world.restoreBuilding, not world.place: the placement rules were already satisfied the
        // first time this building was built — re-checking them now serves no purpose (the same
        // reasoning the save/load path uses).
        if (removed != null) {
            world.restoreBuilding(x, y, removed);
        }
    }
}
