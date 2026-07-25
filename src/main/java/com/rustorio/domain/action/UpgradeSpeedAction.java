package com.rustorio.domain.action;

import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.world.World;
import org.jspecify.annotations.Nullable;

/**
 * Wrap the building on a cell in a {@link SpeedModule}; undo unwraps it back to exactly what it
 * was before (plain, or already wrapped some number of times if this isn't the first upgrade) —
 * the same "remember the object, don't reconstruct it" approach as {@link RemoveAction}.
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
        previous = world.removeBuilding(x, y).orElse(null);
        if (previous != null) {
            world.restoreBuilding(x, y, new SpeedModule(previous));
        }
        return previous != null;
    }

    @Override
    public void undo(World world) {
        if (previous != null) {
            world.restoreBuilding(x, y, previous);
        }
    }
}
