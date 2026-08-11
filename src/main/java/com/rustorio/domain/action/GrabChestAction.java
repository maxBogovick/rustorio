package com.rustorio.domain.action;

import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Hand-collect a {@link Chest}'s entire contents into the player's inventory, leaving the chest
 * standing, empty — a live bug report: a chest's contents and the player's own buildable stock
 * used to be two completely disconnected pools with no way to move items from one into the other,
 * so a factory that had produced plenty could still be unable to afford its own next building.
 *
 * <p>Deliberately NOT a per-item withdrawal (a stack-splitting UI, "take 1"/"take all"): the whole
 * chest at once is the smallest thing that actually fixes the reported problem, and this game has
 * no inventory screen to build a partial-withdrawal UI around anyway.
 */
public final class GrabChestAction implements PlayerAction {

    private final int x;
    private final int y;

    /** What was taken — {@code null} if {@link #apply} found no chest here or an already-empty one (nothing to undo). */
    private @Nullable Map<ItemType, Integer> taken;

    public GrabChestAction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean apply(World world) {
        Building building = world.peek(x, y).orElse(null);
        if (!(building instanceof Chest chest)) {
            return false;
        }
        Map<ItemType, Integer> drained = chest.drain();
        if (drained.isEmpty()) {
            return false; // nothing to grab — not worth remembering for undo
        }
        drained.forEach(world::creditItem);
        taken = drained;
        return true;
    }

    /**
     * Claw the credited items back out of inventory, then hand them back to the chest — in that
     * order, so a player who already spent what was granted simply can't undo this (same "stays
     * applied" compromise {@code RemoveAction}'s own undo makes). If the chest itself is gone
     * (demolished since the grab), this is a no-op too — nowhere left to put the items back.
     *
     * <p>{@link Chest#canRestore} is checked FIRST, before anything is spent (S3,
     * CODE_REVIEW_2026-07-28.md): production may have refilled the chest since the grab, and
     * without this check the restore below would push it past {@code BIG_BUFFER}'s capacity —
     * checking capacity after already deducting the items from inventory would either overfill
     * the chest or, worse, strand the deducted items nowhere.
     */
    @Override
    public void undo(World world) {
        Map<ItemType, Integer> items = taken;
        if (items == null) {
            return;
        }
        Building building = world.peek(x, y).orElse(null);
        if (!(building instanceof Chest chest) || !chest.canRestore(world, items) || !world.trySpendItems(items)) {
            return;
        }
        chest.restore(items);
    }
}
