package com.rustorio.domain.action;

import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Inserter;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.building.Splitter;
import com.rustorio.domain.building.UndergroundBelt;
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
 *
 * <p><b>Owner decision (N13, NEW_BUGS_PROGRESS.md):</b> {@link UndergroundBelt} halves are refused
 * for the same honesty reason, arrived at from the opposite direction. An external review claimed a
 * wrapped tunnel desynchronizes the tick phases; it doesn't — the module's second {@code inner.tick}
 * call finds {@code held} already {@code null} (the first call either relayed the cargo or found
 * none) and returns immediately, so no tunnel ever moves two items in one tick. What's left is a
 * module that provably does nothing at all while still costing the player the upgrade, which is
 * exactly the case P2-03 decided not to sell.
 *
 * <p><b>Extended to {@link Inserter}/{@link Filter}/{@link Splitter} (code review finding).</b> All
 * three share the EXACT same single-slot "accept sets {@code held}+{@code arrivedThisTick}, tick
 * pushes it out and clears {@code held}" shape as {@code Belt}/{@code UndergroundBelt} — the module's
 * second {@code inner.tick} call in the same world tick always finds {@code held == null} (the
 * first call either relayed the held item or found nothing), so it's provably a no-op, same as the
 * tunnel case above. Unlike a belt, there's no "it works on the tail tile" partial truth here to
 * even mislead about — it never does anything, on any tile, for any of the three.
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
        if (bare instanceof Belt || bare instanceof UndergroundBelt
                || bare instanceof Inserter || bare instanceof Filter || bare instanceof Splitter) {
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
