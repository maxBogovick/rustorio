package com.rustorio.domain.action;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;

/**
 * Place a building of the chosen kind facing a direction, paying its {@link
 * com.rustorio.domain.building.BuildingCost} from the player's inventory; undo demolishes exactly
 * that cell and refunds the cost (D-03, DEV_TASKS.md).
 */
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

    /**
     * Charges first, then places — never the other way around, so a player who can't afford this
     * building never even reaches {@code World.place}'s occupancy/rule check. If the charge
     * succeeds but placement then fails for an unrelated reason (the cell turned out to be
     * occupied), the charge is refunded immediately: only an ACTUAL building on the map may hold
     * the player's spent resources.
     */
    @Override
    public boolean apply(World world) {
        if (!world.trySpendBuildingCost(type)) {
            return false;
        }
        boolean placed = world.place(type, x, y, direction);
        if (!placed) {
            world.refundBuildingCost(type);
        }
        return placed;
    }

    /**
     * Undoing a placement removes exactly the cell this action built and refunds the cost it
     * charged — and, for a {@link Chest}, hands back what was stored inside it (N7,
     * NEW_BUGS_PROGRESS.md) instead of destroying it. That last part isn't a special case invented
     * here: {@link RemoveAction#apply} already credits a demolished chest's contents, so without it
     * the same chest holding the same items lost everything or kept everything purely depending on
     * WHICH of the two ways the player took it off the map.
     *
     * <p>Everything a non-chest building accumulated (a furnace's ore buffer, a belt tile's cargo)
     * still goes with it, exactly as before: those buffers are in-flight production, not stored
     * goods the player ever owned, and no other code path returns them either.
     */
    @Override
    public void undo(World world) {
        world.peek(x, y)
                .map(Building::unwrap)
                .filter(Chest.class::isInstance)
                .map(Chest.class::cast)
                .ifPresent(chest -> chest.drain().forEach(world::creditItem));
        world.removeBuilding(x, y);
        world.refundBuildingCost(type);
    }
}
