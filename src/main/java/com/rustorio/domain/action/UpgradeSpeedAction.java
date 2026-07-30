package com.rustorio.domain.action;

import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.world.World;
import org.jspecify.annotations.Nullable;

/**
 * Wrap the building on a cell in a {@link SpeedModule}; undo unwraps it back to exactly what it
 * was before (plain, or already wrapped some number of times if this isn't the first upgrade) —
 * the same "remember the object, don't reconstruct it" approach as {@link RemoveAction}.
 *
 * <p><b>Which kinds refuse the upgrade, and why.</b> Read from {@code
 * BuildingPrototype.acceptsSpeedEffects()} — a registered property of the kind, not an {@code
 * instanceof} chain over concrete classes. {@code false} for a belt, a tunnel half, an inserter, a
 * filter, and a splitter — all six share the same single-slot or segment-joining shape where a
 * {@link SpeedModule}'s second {@code inner.tick} call in the same world tick either does nothing
 * (single-slot: {@code held} is already {@code null} after the first call relayed or found nothing)
 * or, for a belt specifically, doubles the WHOLE segment's move (however long) rather than just one
 * tile — unpredictable either way, and refusing it outright is honest about what this module can't
 * do (P2-03/N13, BUG_FIX_PROGRESS.md/NEW_BUGS_PROGRESS.md; extended to inserter/filter/splitter by
 * a later code review finding). {@code true} for everything else, {@link
 * com.rustorio.domain.building.Chest} included: {@code Chest.tick} genuinely pushes one item per
 * tick, so a doubled call is a real, meaningful effect, not a no-op.
 */
public final class UpgradeSpeedAction implements PlayerAction {

    private final int x;
    private final int y;

    /**
     * What stood there BEFORE the upgrade — captured in {@link #apply}, restored in {@link
     * #undo}. Plain nullable field, not {@code Optional<Building>} — see {@link RemoveAction
     * #removed} for why.
     */
    private @Nullable Building previous;

    /**
     * The building's ANCHOR cell, resolved via {@link World#originOf} in {@link #apply} — may
     * differ from {@link #x}/{@link #y} for a multi-cell building (X-03, DEV_TASKS.md) clicked on
     * a non-anchor cell. Every {@code removeBuilding}/{@code restoreBuilding} call below uses this,
     * never the raw {@link #x}/{@link #y} — same reasoning as {@link RemoveAction}/{@link
     * RotateAction}. Before this fix, upgrading a multi-cell building from a non-anchor cell
     * re-anchored it at the CLICKED cell instead of its real origin, silently relocating it and
     * corrupting the occupancy map (found in code review).
     */
    private int anchorX;
    private int anchorY;

    public UpgradeSpeedAction(int x, int y) {
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
        Building removed = world.removeBuilding(anchorX, anchorY).orElse(null);
        if (removed == null) {
            return false;
        }
        Building bare = Building.unwrap(removed);
        if (!world.buildingFactory().prototype(bare.type()).acceptsSpeedEffects()) {
            world.restoreBuilding(anchorX, anchorY, removed); // put it right back — see the class javadoc
            return false;
        }
        previous = removed;
        world.restoreBuilding(anchorX, anchorY, new SpeedModule(removed));
        return true;
    }

    @Override
    public void undo(World world) {
        if (previous != null) {
            world.restoreBuilding(anchorX, anchorY, previous);
        }
    }
}
