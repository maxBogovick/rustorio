package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.world.World;

/** Place a building of the chosen kind facing a direction; undo demolishes exactly that cell. */
public final class PlaceAction implements PlayerAction {

    private final BuildingType type;
    private final int x;
    private final int y;
    private final Direction direction;

    public PlaceAction(BuildingType type, int x, int y) {
        this(type, x, y, Direction.RIGHT); // direction only matters to belts, furnaces, tunnels, splitters
    }

    public PlaceAction(BuildingType type, int x, int y, Direction direction) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    @Override
    public boolean apply(World world) {
        return world.place(type, x, y, direction);
    }

    @Override
    public void undo(World world) {
        // Whatever grew there between apply and undo (ore in a furnace, a chest's count) doesn't
        // matter: this cell was built by this action, so undoing it just means removing it.
        world.removeBuilding(x, y);
    }
}
