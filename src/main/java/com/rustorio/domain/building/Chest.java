package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A real buffer: stores what it's handed, by kind, up to a total capacity, and gives it back out
 * — the fix for §2.5 of the design audit, which called the old {@code Chest} "not a container, an
 * incinerator": item type was thrown away on {@link #accept}, capacity was infinite (so {@link
 * Tech#BIG_BUFFER} did nothing to it), and nothing could ever be taken back out.
 *
 * <p><b>Owner decision (D-02, DEV_TASKS.md):</b> a chest is now directional, like {@link Miner}
 * became in D-01 — {@link #tick} pushes ONE item per tick out through {@link #direction} via
 * {@link TickContext#offerForward}, trying each {@link Item} kind in enum declaration order
 * (deterministic, not "whichever {@code Map} iteration happens to hit first"). Making it
 * directional means it also needs {@link #rotatedClockwise} — the same D-01 pairing rule applies:
 * a chest built facing the wrong way, with no way to turn it, would be exactly the kind of trap
 * D-01 already fixed for miners.
 */
public final class Chest implements Building {

    /**
     * Total items across every kind, not per kind — matches the card's own "предел суммарной
     * ёмкости" (total capacity limit), not a separate cap per item type. Not derived from the
     * audit (which names no number): a bulk buffer should hold noticeably more than a machine's
     * few-unit input slot ({@link Furnace}/{@link Lab} both cap at 5), so this is deliberately an
     * order of magnitude bigger.
     */
    private static final int CAPACITY = 100;

    private final Direction direction;
    private final Map<Item, Integer> contents = new EnumMap<>(Item.class);
    /**
     * Running total across every kind in {@link #contents}, kept in step with it rather than summed
     * on demand (N17, NEW_BUGS_PROGRESS.md — owner decision). {@link #accept} and {@link #appearance}
     * both need it, the first on every offered item and the second on every rendered frame, and the
     * only way to get it wrong is to change {@link #contents} without going through the four methods
     * below — which is why every one of them is here, in this class, with nothing else touching that
     * map.
     */
    private int storedCount;
    /** Recomputed once per {@link #tick}, not once per render frame — see {@link BuildingStatus}'s own javadoc for why (F-01, DEV_TASKS.md). */
    private BuildingStatus status = BuildingStatus.WORKING;

    /** Convenience for call sites that only care about {@link #accept}, not output direction — same reasoning as {@code PlaceAction}'s no-direction overload. */
    public Chest() {
        this(Direction.RIGHT);
    }

    public Chest(Direction direction) {
        this.direction = direction;
    }

    /**
     * Package-private restore constructor used by {@link BuildingFactory#restore} — status isn't
     * part of {@link BuildingMemento.ChestState} (it's ephemeral, recomputed on the next {@link
     * #tick}, same call already made for every other building's status), so this always starts
     * fresh at the default {@code WORKING}.
     */
    Chest(Direction direction, Map<Item, Integer> contents) {
        this(direction, contents, BuildingStatus.WORKING);
    }

    /**
     * The general form both the restore constructor above and {@link #rotatedClockwise} use —
     * {@code rotatedClockwise} passes the CURRENT {@link #status} through (code review finding):
     * unlike a save/load restore, a rotation doesn't create a new logical chest, so a chest that
     * was actually {@code OUTPUT_FULL} must not flash back to {@code WORKING} — even for one tick
     * — just because the player rotated it. {@code HudRenderer.alerts()} reads {@code
     * World.statusCounts()} directly now, so a stale reset here is directly visible on the HUD.
     */
    private Chest(Direction direction, Map<Item, Integer> contents, BuildingStatus status) {
        this.direction = direction;
        this.contents.putAll(contents);
        for (int quantity : this.contents.values()) {
            storedCount += quantity;
        }
        this.status = status;
    }

    @Override
    public boolean accept(TickContext world, Item item) {
        if (totalCount() >= effectiveCapacity(world)) {
            return false;
        }
        contents.merge(item, 1, Integer::sum);
        storedCount++;
        return true;
    }

    /** Push one stored item (whichever kind comes first in {@link Item} declaration order) out through {@link #direction}. */
    @Override
    public void tick(TickContext world, int x, int y) {
        // At capacity is a real problem worth surfacing (F-01, DEV_TASKS.md, §2.5 of the audit's
        // own "заполненный ящик — тоже OUTPUT_FULL" note) — computed here, not in accept()/appearance(),
        // since only tick() has both the current contents AND TickContext (for BIG_BUFFER) at once.
        for (Item item : Item.values()) {
            if (contents.getOrDefault(item, 0) > 0
                    && world.offerForward(x + direction.dx(), y + direction.dy(), item)) {
                decrement(item);
                // One item per tick, same discipline as every other building — and a chest that
                // just delivered is working, whatever it weighed a moment ago (N6,
                // NEW_BUGS_PROGRESS.md): computing the status BEFORE this push, as this method used
                // to, left a full-but-flowing chest rendered as blocked for the whole frame.
                status = BuildingStatus.WORKING;
                return;
            }
        }
        status = totalCount() >= effectiveCapacity(world) ? BuildingStatus.OUTPUT_FULL : BuildingStatus.WORKING;
    }

    private void decrement(Item item) {
        int have = contents.getOrDefault(item, 0);
        if (have <= 1) {
            contents.remove(item);
        } else {
            contents.put(item, have - 1);
        }
        if (have > 0) {
            storedCount--;
        }
    }

    /** Total items stored, across every kind — what the badge on the sprite shows. */
    public int count() {
        return totalCount();
    }

    /** How many of exactly {@code item} are stored — the per-kind breakdown (for F-03's inspection panel, later). */
    public int amount(Item item) {
        return contents.getOrDefault(item, 0);
    }

    /**
     * A snapshot of everything stored, by kind — for demolition refund ({@code RemoveAction}) and
     * hand-collection ({@code GrabChestAction}). A live bug report: before these existed, a
     * chest's contents and the player's own buildable stock were two completely disconnected
     * pools — a factory that had produced plenty could still be unable to afford its own next
     * building, and demolishing a full chest silently destroyed everything inside it.
     */
    public Map<Item, Integer> contents() {
        return Map.copyOf(contents);
    }

    /** Empty this chest completely, returning what was taken — see {@link #contents()}'s javadoc. */
    public Map<Item, Integer> drain() {
        Map<Item, Integer> taken = Map.copyOf(contents);
        contents.clear();
        storedCount = 0;
        return taken;
    }

    /**
     * Add back a batch previously taken via {@link #drain} — {@code GrabChestAction}'s undo.
     * Bypasses {@link #accept}'s one-item-at-a-time/capacity checks on purpose: this restores
     * EXACTLY what was drained, in one shot, not a normal delivery that should be capped item by
     * item. Callers that can't guarantee the chest is still empty (production may have refilled it
     * since the grab) must check {@link #canRestore} first — see that method's javadoc.
     */
    public void restore(Map<Item, Integer> items) {
        items.forEach((item, amount) -> {
            contents.merge(item, amount, Integer::sum);
            storedCount += amount;
        });
    }

    /**
     * Whether handing {@code items} to {@link #restore} would fit within {@link
     * #effectiveCapacity} — {@code GrabChestAction.undo} checks this BEFORE spending anything back
     * out of the player's inventory (S3, CODE_REVIEW_2026-07-28.md): a chest emptied by a grab can
     * fill right back up from ongoing production before the player presses undo, and without this
     * check {@link #restore} would push it past capacity — the same "not by the player's own
     * economy" bypass {@code BIG_BUFFER} is supposed to gate. Checking first, instead of letting
     * {@link #restore} fail after the fact, also means the player never loses items already
     * deducted from inventory to an undo that doesn't fit.
     */
    public boolean canRestore(TickContext world, Map<Item, Integer> items) {
        int sum = 0;
        for (int amount : items.values()) {
            sum += amount;
        }
        return totalCount() + sum <= effectiveCapacity(world);
    }

    /** Total items stored across every kind — see {@link #storedCount}. */
    private int totalCount() {
        return storedCount;
    }

    private static int effectiveCapacity(TickContext world) {
        return world.research().biggerIfUnlocked(Tech.BIG_BUFFER, CAPACITY);
    }

    @Override
    public Appearance appearance() {
        int total = totalCount();
        return total > 0 ? Appearance.of(Sprite.CHEST, total, status) : Appearance.of(Sprite.CHEST, status);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Chest(direction.rotate(), contents, status));
    }

    @Override
    public BuildingType type() {
        return BuildingType.CHEST;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.ChestState(direction, contents);
    }
}
