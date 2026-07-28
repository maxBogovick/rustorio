package com.rustorio.domain.world;

import com.rustorio.domain.Item;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What the player has on hand to spend on construction (D-03, DEV_TASKS.md) — owned by {@link
 * World}, mutated only through it ({@code World.trySpendBuildingCost}/{@code refundBuildingCost}),
 * exactly the same shape as {@link ProductionStats}: a plain counter map plus a read-only view
 * ({@link PlayerInventoryView}) for anyone outside {@code World} that only needs to look.
 *
 * <p>Public, not package-private, since D-07 (DEV_TASKS.md): {@code
 * com.rustorio.persistence.JsonSaveRepository} needs to name {@link Snapshot} directly to persist
 * it, the same way it already names {@code ProductionStats.Snapshot}/{@code Research.Snapshot}.
 */
public final class PlayerInventory implements PlayerInventoryView {

    private final Map<Item, Integer> amounts = new EnumMap<>(Item.class);

    @Override
    public int amount(Item item) {
        return amounts.getOrDefault(item, 0);
    }

    /** Credit {@code quantity} of {@code item} — used for both the starting stock and every refund. */
    void add(Item item, int quantity) {
        amounts.merge(item, quantity, Integer::sum);
    }

    /**
     * Spend {@code quantity} of {@code item} if there's enough on hand, atomically: either the full
     * amount is deducted and this returns {@code true}, or nothing changes and it returns {@code
     * false} — never a partial spend.
     */
    boolean trySpend(Item item, int quantity) {
        int have = amount(item);
        if (have < quantity) {
            return false;
        }
        amounts.put(item, have - quantity);
        return true;
    }

    /**
     * Same atomicity as {@link #trySpend}, for several items at once: checked first, then spent —
     * either every requested item is available and every one gets deducted, or nothing changes at
     * all. Used by {@code World.trySpendItems} (RemoveAction/GrabChestAction's undo).
     */
    boolean trySpendAll(Map<Item, Integer> items) {
        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            // Negative quantities are refused, not just "affordable" (N4, NEW_BUGS_PROGRESS.md):
            // have < -100 is trivially true-passing, and the deduction below would then compute
            // have - (-100) and CREDIT 100 items. The amounts reaching here come from a chest's
            // contents, i.e. out of a save file — data this class doesn't get to assume is sane.
            if (entry.getValue() < 0 || amount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            amounts.put(entry.getKey(), amount(entry.getKey()) - entry.getValue());
        }
        return true;
    }

    /** Reset to nothing — see {@code World.clear()}. */
    void clear() {
        amounts.clear();
    }

    /** Immutable point-in-time snapshot for persistence (Memento pattern) — see {@code JsonSaveRepository} (D-07). */
    public record Snapshot(Map<Item, Integer> amounts) {
        public Snapshot {
            // new EnumMap<>(Map) throws ClassCastException on an empty non-EnumMap argument (can't
            // infer the key type from zero entries) — the exact trap BuildingMemento.ChestState's
            // compact constructor already documents; Jackson hands one of these for a save written
            // with an empty inventory.
            Map<Item, Integer> copy = new EnumMap<>(Item.class);
            copy.putAll(amounts);
            amounts = Collections.unmodifiableMap(copy);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(amounts);
    }

    /** Overwrite wholesale from a save file — see {@code World.restoreInventory}. */
    void restore(Snapshot snapshot) {
        clear();
        amounts.putAll(snapshot.amounts());
    }
}
