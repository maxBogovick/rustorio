package com.rustorio.domain.world;

import com.rustorio.api.content.model.ItemType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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

    // HashMap, not TreeMap (code review finding S6): never iterated directly — every read here is
    // a point lookup (amount/trySpend/add), and HudRenderer.inventoryChips walks the ITEM
    // REGISTRY's own rawId order, only point-looking-up inventory.amount(item) per entry. A
    // TreeMap here regressed HUD chip layout to O(log n) ContentId-string comparisons per lookup,
    // every frame, for no observable benefit — snapshot() below already rebuilds its own TreeMap
    // for the one place iteration order actually matters (persistence).
    private final Map<ItemType, Integer> amounts = new HashMap<>();

    @Override
    public int amount(ItemType item) {
        return amounts.getOrDefault(item, 0);
    }

    /** Credit {@code quantity} of {@code item} — used for both the starting stock and every refund. */
    void add(ItemType item, int quantity) {
        amounts.merge(item, quantity, Integer::sum);
    }

    /**
     * Spend {@code quantity} of {@code item} if there's enough on hand, atomically: either the full
     * amount is deducted and this returns {@code true}, or nothing changes and it returns {@code
     * false} — never a partial spend.
     */
    boolean trySpend(ItemType item, int quantity) {
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
    boolean trySpendAll(Map<ItemType, Integer> items) {
        for (Map.Entry<ItemType, Integer> entry : items.entrySet()) {
            // Negative quantities are refused, not just "affordable" (N4, NEW_BUGS_PROGRESS.md):
            // have < -100 is trivially true-passing, and the deduction below would then compute
            // have - (-100) and CREDIT 100 items. The amounts reaching here come from a chest's
            // contents, i.e. out of a save file — data this class doesn't get to assume is sane.
            if (entry.getValue() < 0 || amount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        for (Map.Entry<ItemType, Integer> entry : items.entrySet()) {
            amounts.put(entry.getKey(), amount(entry.getKey()) - entry.getValue());
        }
        return true;
    }

    /** Reset to nothing — see {@code World.clear()}. */
    void clear() {
        amounts.clear();
    }

    /** Immutable point-in-time snapshot for persistence (Memento pattern) — see {@code JsonSaveRepository} (D-07). */
    public record Snapshot(Map<ItemType, Integer> amounts) {
        public Snapshot {
            // TreeMap, not a plain HashMap Jackson would otherwise deserialize into: iteration
            // order must stay ItemType's natural (ContentId/rawId) order, not JVM-run-dependent
            // bucket order — see ItemType's own javadoc on why that determinism matters here.
            amounts = Collections.unmodifiableMap(new TreeMap<>(amounts));
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
