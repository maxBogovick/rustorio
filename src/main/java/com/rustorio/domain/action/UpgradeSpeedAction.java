package com.rustorio.domain.action;

import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.world.World;
import org.jspecify.annotations.Nullable;

/**
 * Wrap the building on a cell in a {@link SpeedModule}; undo unwraps it back to exactly what it
 * was before (plain, or already wrapped some number of times if this isn't the first upgrade) —
 * the same "remember the object, don't reconstruct it" approach as {@link RemoveAction}.
 *
 * <p><b>Owner decision (P2-03, BUG_FIX_PROGRESS.md):</b> option (A) — {@link Belt}s are refused. A
 * {@link SpeedModule} calls {@code inner.tick} twice, but a belt only actually moves cargo when
 * ticked on its segment's tail tile ({@code Belt.tick}); on any other tile the second call is a
 * no-op, and on the tail it doubles the WHOLE segment (however long), not just this one tile.
 * Which tile is the tail isn't visible to the player and shifts on every merge/split, so the
 * effect of upgrading a belt tile is unpredictable by design — refusing it outright is honest
 * about what this module can't do, rather than pretending to support it.
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

    public UpgradeSpeedAction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean apply(World world) {
        Building removed = world.removeBuilding(x, y).orElse(null);
        if (removed == null) {
            return false;
        }
        if (Building.unwrap(removed) instanceof Belt) {
            world.restoreBuilding(x, y, removed); // put it right back — belts don't take this module
            return false;
        }
        previous = removed;
        world.restoreBuilding(x, y, new SpeedModule(removed));
        return true;
    }

    @Override
    public void undo(World world) {
        if (previous != null) {
            world.restoreBuilding(x, y, previous);
        }
    }
}
