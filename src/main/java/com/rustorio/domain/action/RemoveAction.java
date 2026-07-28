package com.rustorio.domain.action;

import com.rustorio.domain.Item;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Demolish the building on a cell, refunding its {@link com.rustorio.domain.building.BuildingCost}
 * to the player's inventory (D-03, DEV_TASKS.md); undo puts back the very object that was
 * demolished, re-charging that same cost.
 *
 * <p><b>Owner decision (live bug report):</b> demolishing a {@link Chest} also refunds whatever it
 * was holding — before this, a full chest's contents simply vanished, refunding only the chest's
 * OWN construction cost. Undo claws the credited contents back out of the player's inventory
 * before restoring the chest, exactly the same "can't afford to undo, stays applied" compromise
 * already made for the building cost alone (see {@link #undo}'s own javadoc) — a player who
 * already spent what a demolished chest handed them can't un-spend it just by pressing Ctrl+Z.
 */
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

    /** What a demolished {@link Chest} was holding, credited to inventory in {@link #apply} — {@code null} for every other building kind, or an empty chest. */
    private @Nullable Map<Item, Integer> reclaimedContents;

    /**
     * The demolished building's ANCHOR cell, resolved via {@link World#originOf} in {@link
     * #apply} — may differ from {@link #x}/{@link #y} for a multi-cell building (X-03,
     * DEV_TASKS.md) demolished by clicking a non-anchor cell. {@link #undo} restores here, not at
     * {@code x, y}, for the same reason {@link RotateAction} does — see its own javadoc.
     */
    private int anchorX;
    private int anchorY;

    public RemoveAction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean apply(World world) {
        World.Coord origin = world.originOf(x, y).orElse(null);
        if (origin == null) {
            return false;
        }
        anchorX = origin.x();
        anchorY = origin.y();
        removed = world.removeBuilding(anchorX, anchorY).orElse(null);
        if (removed != null) {
            // removed.type(), not Building.unwrap(removed).type(): SpeedModule already delegates
            // type() to what it wraps, so this refunds the real building's cost either way. The
            // SpeedModule upgrade itself stays free either direction — a separate, already-known
            // audit finding (§3.8) this task doesn't touch.
            world.refundBuildingCost(removed.type());
            if (Building.unwrap(removed) instanceof Chest chest) {
                Map<Item, Integer> contents = chest.contents();
                if (!contents.isEmpty()) {
                    reclaimedContents = contents;
                    contents.forEach(world::creditItem);
                }
            }
        }
        return removed != null;
    }

    /**
     * If the cell is occupied by something built after the demolition, undo becomes a no-op — the
     * newer building takes priority over restoring the older one, and {@link #removed} is simply
     * left un-restored (not lost: it just stays undone, same as any other failed undo). See P1-07
     * in BUG_FIX_PROGRESS.md.
     *
     * <p>D-03 adds a second precondition, checked only once the cell is confirmed free: the player
     * must be able to afford re-charging this building's cost right now. Order matters — {@code
     * isFree} is checked first so an occupied cell never charges the player for a restore that
     * isn't going to happen. If the charge itself fails (spent the refund on something else since
     * demolishing), undo stays a no-op too — the alternative would let {@code Ctrl+Z} hand back a
     * building for resources the player no longer has.
     *
     * <p>A third precondition, only for a demolished {@link Chest} with {@link #reclaimedContents}:
     * the player must ALSO be able to pay back what demolition credited them, checked (and rolled
     * back on failure) only after the building cost itself already succeeded — same "stays a no-op"
     * rule, just one more thing that has to be affordable for the whole undo to go through.
     *
     * <p>The free-cell check uses {@link World#footprintFree}, not {@link World#isFree}, sized by
     * {@link Building#footprintWidth}/{@link Building#footprintHeight} (X-03, DEV_TASKS.md): for a
     * multi-cell building, checking only the anchor cell would miss a NEW building placed into one
     * of its OTHER cells since the demolition — restoring over it would silently corrupt that
     * other building's occupancy. For every 1x1 building this is exactly {@link World#isFree},
     * unchanged.
     */
    @Override
    public void undo(World world) {
        if (removed == null
                || !world.footprintFree(anchorX, anchorY, removed.footprintWidth(), removed.footprintHeight())
                || !world.trySpendBuildingCost(removed.type())) {
            return;
        }
        if (reclaimedContents != null && !world.trySpendItems(reclaimedContents)) {
            world.refundBuildingCost(removed.type()); // roll back the charge just above — all-or-nothing
            return;
        }
        // world.restoreBuilding, not world.place: the placement rules were already satisfied the
        // first time this building was built — re-checking them now serves no purpose (the same
        // reasoning the save/load path uses).
        world.restoreBuilding(anchorX, anchorY, removed);
    }
}
