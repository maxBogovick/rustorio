package com.rustorio.domain.action;

import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Hand-deposit one item kind from the player's inventory into a {@link Chest} under the cursor —
 * as many units as fit. The inverse of {@link GrabChestAction}: grab pulls the chest into the
 * buildable stock; this puts stock back onto the factory floor so a line can be fed by hand.
 *
 * <p>One kind at a time (not "dump the whole inventory"): the player picks which stack in the
 * always-on inventory panel, then aims at a chest. Dumping everything would scramble carefully
 * sorted buffers the moment someone missed a click.
 */
public final class DepositChestAction implements PlayerAction {

    private final int x;
    private final int y;
    private final ItemType item;

    /** How many units landed — {@code null} if {@link #apply} did nothing (nothing to undo). */
    private @Nullable Integer deposited;

    public DepositChestAction(int x, int y, ItemType item) {
        this.x = x;
        this.y = y;
        this.item = item;
    }

    @Override
    public boolean apply(World world) {
        Building building = world.peek(x, y).orElse(null);
        if (!(building instanceof Chest chest)) {
            return false;
        }
        int have = world.inventory().amount(item);
        if (have <= 0) {
            return false;
        }
        int space = chest.freeSpace(world);
        if (space <= 0) {
            return false;
        }
        int amount = Math.min(have, space);
        Map<ItemType, Integer> batch = new LinkedHashMap<>();
        batch.put(item, amount);
        if (!world.trySpendItems(batch)) {
            return false;
        }
        chest.restore(batch);
        deposited = amount;
        return true;
    }

    /**
     * Pull the deposited units back into inventory and out of the chest — refuses if the chest no
     * longer holds that many (a belt may have drained it since the deposit).
     */
    @Override
    public void undo(World world) {
        Integer amount = deposited;
        if (amount == null) {
            return;
        }
        Building building = world.peek(x, y).orElse(null);
        if (!(building instanceof Chest chest)) {
            return;
        }
        int taken = chest.take(item, amount);
        if (taken > 0) {
            world.creditItem(item, taken);
        }
    }
}
