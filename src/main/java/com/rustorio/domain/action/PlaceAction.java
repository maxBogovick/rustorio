package com.rustorio.domain.action;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Place a building of the chosen prototype facing a direction, paying its {@link
 * com.rustorio.domain.building.BuildingCost} from the player's inventory; undo demolishes exactly
 * that cell and refunds the cost (D-03, DEV_TASKS.md).
 *
 * <p>Holds a {@link ContentId}, not a {@link BuildingType} — any registered prototype, vanilla or
 * modded, can be placed through this action now. The {@link BuildingType} constructors are a
 * convenience for the closed vanilla set (resolves {@code type}'s own prototype id via {@link
 * VanillaBuildings#idFor}), not a separate code path: both eventually build the same action.
 */
public final class PlaceAction implements PlayerAction {

    private final ContentId prototypeId;
    private final int x;
    private final int y;
    private final Direction direction;

    /**
     * A demolished-by-undo {@link Chest}'s contents, saved so a REDO restores the SAME chest
     * instead of a blank one (code review finding): {@link #apply} always calls {@code
     * World.place}, which builds a brand-new instance — without this, undo credited a chest's
     * contents to inventory (see {@link #undo}) but redo built an empty replacement, silently
     * losing them from the chest while they stayed spendable in inventory. Claimed back out of
     * inventory in {@link #apply} before being restored — same "stays applied if you already spent
     * it" discipline {@code RemoveAction#undo}/{@code GrabChestAction#undo} already use, just on
     * the redo side of the stack instead of the undo side.
     */
    private @Nullable Map<ItemType, Integer> savedChestContents;

    public PlaceAction(BuildingType type, int x, int y) {
        this(VanillaBuildings.idFor(type), x, y);
    }

    public PlaceAction(BuildingType type, int x, int y, Direction direction) {
        this(VanillaBuildings.idFor(type), x, y, direction);
    }

    public PlaceAction(ContentId prototypeId, int x, int y) {
        this(prototypeId, x, y, Direction.RIGHT); // direction only matters to belts, furnaces, tunnels, splitters
    }

    public PlaceAction(ContentId prototypeId, int x, int y, Direction direction) {
        this.prototypeId = prototypeId;
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
     *
     * <p>On a REDO — {@link #savedChestContents} non-{@code null} — also claims those items back
     * out of inventory and restores them into the freshly placed chest (code review finding): see
     * that field's own javadoc for why. If the player already spent what undo credited them, the
     * {@code trySpendItems} below simply fails and the new chest stays empty — the "stays applied"
     * compromise, not a hard error; the building itself was already paid for and stands regardless.
     */
    @Override
    public boolean apply(World world) {
        if (!world.trySpendBuildingCost(prototypeId)) {
            return false;
        }
        boolean placed = world.place(prototypeId, x, y, direction);
        if (!placed) {
            world.refundBuildingCost(prototypeId);
            return false;
        }
        Map<ItemType, Integer> contents = savedChestContents;
        if (contents != null) {
            if (world.trySpendItems(contents)) {
                world.peek(x, y)
                        .filter(Chest.class::isInstance)
                        .map(Chest.class::cast)
                        .ifPresent(chest -> chest.restore(contents));
            }
            savedChestContents = null;
        }
        return true;
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
     *
     * <p>Drained contents are also remembered in {@link #savedChestContents} for {@link #apply}'s
     * redo path (code review finding) — before this, redo built a brand-new EMPTY chest, silently
     * losing whatever undo had just credited to inventory, an asymmetric undo/redo pair unlike
     * every other multi-step action in this package.
     */
    @Override
    public void undo(World world) {
        world.peek(x, y)
                .filter(Chest.class::isInstance)
                .map(Chest.class::cast)
                .ifPresent(chest -> {
                    Map<ItemType, Integer> contents = chest.drain();
                    if (!contents.isEmpty()) {
                        savedChestContents = contents;
                        contents.forEach(world::creditItem);
                    }
                });
        world.removeBuilding(x, y);
        world.refundBuildingCost(prototypeId);
    }
}
