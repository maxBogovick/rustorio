package com.rustorio.domain.action;

import com.rustorio.domain.building.Building;
import com.rustorio.domain.world.World;
import org.jspecify.annotations.Nullable;

/**
 * Turn the building already standing on a cell one clockwise step, without demolishing it —
 * {@link Building#rotatedClockwise()} builds the replacement, this class just swaps it in and
 * remembers the original for undo. Refuses (returns {@code false}, remembers nothing) for a
 * building with no direction to rotate (a {@code Chest}, a {@code Lab}) and for an empty cell.
 *
 * <p><b>Owner decision (D-01, DEV_TASKS.md):</b> before this action existed, the only way to fix a
 * production building facing the wrong way was to demolish and rebuild it — tolerable while
 * delivery was broadcast to all four neighbors regardless of facing, but a real trap once delivery
 * became addressed (see {@link com.rustorio.domain.building.Miner}'s class javadoc). Modeled after
 * {@link UpgradeSpeedAction}: remove, transform, restore; undo restores the original object
 * unconditionally, the same simplification that action already makes (contrast {@link
 * RemoveAction#undo}, which checks the cell is still free — irrelevant here, since a rotation never
 * frees the cell for something else to be built on top of).
 */
public final class RotateAction implements PlayerAction {

    private final int x;
    private final int y;

    /** What stood there BEFORE the rotation — captured in {@link #apply}, restored in {@link #undo}. */
    private @Nullable Building previous;

    /**
     * The rotated instance {@link #apply} actually put on the map — {@link #undo}'s proof that the
     * cell still holds ITS result and nothing newer (N8, NEW_BUGS_PROGRESS.md). Compared by
     * identity, not equality: {@link Building} implementations are mutable and have no {@code
     * equals}, and "the very object I placed" is exactly the question being asked.
     */
    private @Nullable Building rotated;

    /**
     * The building's ANCHOR cell, resolved via {@link World#originOf} in {@link #apply} — may
     * differ from {@link #x}/{@link #y} for a multi-cell building (X-03, DEV_TASKS.md) clicked on
     * a non-anchor cell. {@link #undo} restores here, not at {@code x, y}: restoring at the raw
     * clicked cell would shift the building to a new position instead of putting it back where it
     * actually stood.
     */
    private int anchorX;
    private int anchorY;

    public RotateAction(int x, int y) {
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
        Building current = world.peek(anchorX, anchorY).orElse(null);
        if (current == null) {
            return false;
        }
        Building turned = current.rotatedClockwise().orElse(null);
        if (turned == null) {
            return false;
        }
        previous = world.removeBuilding(anchorX, anchorY).orElse(null);
        world.restoreBuilding(anchorX, anchorY, turned);
        rotated = turned;
        return true;
    }

    /**
     * A no-op unless the anchor cell still holds the very building {@link #apply} put there (N8,
     * NEW_BUGS_PROGRESS.md) — the same "the newer building wins" rule {@link RemoveAction#undo}
     * already follows, and for the same reason: an undo stack is only ordered, not isolated. A
     * failed or refused undo higher in the stack (an unaffordable {@code RemoveAction}, a redo that
     * couldn't apply) leaves this entry reachable while the cell has since changed hands, and
     * restoring blindly would demolish a building this action never touched.
     */
    @Override
    public void undo(World world) {
        if (previous == null || world.peek(anchorX, anchorY).orElse(null) != rotated) {
            return;
        }
        world.removeBuilding(anchorX, anchorY);
        world.restoreBuilding(anchorX, anchorY, previous);
    }
}
