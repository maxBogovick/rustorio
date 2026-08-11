package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaTechEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A real buffer: stores what it's handed, by kind, up to a total capacity, and gives it back out
 * — the fix for §2.5 of the design audit, which called the old {@code Chest} "not a container, an
 * incinerator": item type was thrown away on {@link #accept}, capacity was infinite (so {@link
 * VanillaTechs#BIG_BUFFER} did nothing to it), and nothing could ever be taken back out.
 *
 * <p><b>Owner decision (D-02, DEV_TASKS.md):</b> a chest is now directional, like {@link Miner}
 * became in D-01 — {@link #tick} pushes ONE item per tick out through {@link #direction} via
 * {@link TickContext#offerForward}, trying each {@link ItemType} kind in {@code rawId} order
 * (deterministic, not "whichever {@code Map} iteration happens to hit first"). Making it
 * directional means it also needs {@link #rotatedClockwise} — the same D-01 pairing rule applies:
 * a chest built facing the wrong way, with no way to turn it, would be exactly the kind of trap
 * D-01 already fixed for miners.
 *
 * <p>{@link SettlesEachTick}: without an arrival mark, a LEFT/UP chest chain multi-hopped in one
 * world tick — descending pass ticks the upstream cell first, {@link #accept} fills the next chest,
 * and that chest then pushed in the same pass. Same failure mode {@link Splitter} documents for
 * relays; chests push from inventory rather than a single held slot, but the settle rule is
 * identical: cargo that arrived this tick does not leave until the next.
 */
public final class Chest implements Building, SettlesEachTick, InspectableBuilding {

    /**
     * Total items across every kind, not per kind — matches the card's own "предел суммарной
     * ёмкости" (total capacity limit), not a separate cap per item type. Not derived from the
     * audit (which names no number): a bulk buffer should hold noticeably more than a machine's
     * few-unit input slot ({@link Furnace}/{@link Lab} both cap at 5), so this is deliberately an
     * order of magnitude bigger.
     */
    private static final int CAPACITY = 100;

    private final Direction direction;
    /** Which sprite {@link #appearance} draws — see {@link Furnace}'s own field javadoc for why this is injected rather than a hardcoded sprite constant. */
    private final BuildingPrototype prototype;
    // HashMap, not TreeMap: tick() below sorts a snapshot of the keys itself (deterministic
    // output order without needing the map's own iteration order to be), and state() hands this
    // to ChestState, whose own compact constructor already re-sorts into a TreeMap for the
    // canonical dump — sorting twice would be pure waste on Chest's own much hotter
    // accept()/tick() path (a TreeMap here measurably regressed the benchmark).
    private final Map<ItemType, Integer> contents = new HashMap<>();
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
    /** {@code UpgradeSpeedAction}'s upgrade count — see {@link #tick}'s own note on how it's applied. */
    private int speedLevel;
    /**
     * True for the rest of the CURRENT world tick if this chest received cargo via {@link #accept}
     * earlier in the same tick — see the class javadoc. {@code TickScheduler} clears it once before
     * either pass runs.
     */
    private boolean arrivedThisTick;

    /** Convenience for call sites that only care about {@link #accept}, not output direction — same reasoning as {@code PlaceAction}'s no-direction overload. */
    public Chest() {
        this(Direction.RIGHT);
    }

    /** Convenience for callers that only care about the vanilla prototype — see the 2-arg constructor for real injection (a modded "big chest" needs its own prototype here). */
    public Chest(Direction direction) {
        this(direction, VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.CHEST)));
    }

    public Chest(Direction direction, BuildingPrototype prototype) {
        this.direction = direction;
        this.prototype = prototype;
    }

    /** Convenience restore constructor for callers that only care about the vanilla prototype — see the 4-arg restore constructor for real injection. */
    Chest(Direction direction, Map<ItemType, Integer> contents, int speedLevel) {
        this(direction, contents, speedLevel, VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.CHEST)));
    }

    /**
     * Package-private restore constructor used by {@link BuildingFactory#restore} (via this
     * prototype's own registered {@code RestoreFactory}) — status isn't part of {@link
     * ChestState} (it's ephemeral, recomputed on the next {@link #tick}, same call already made
     * for every other building's status), so this always starts fresh at the default {@code
     * WORKING}. {@code speedLevel} IS part of {@link ChestState} (a plain field, since this
     * phase's own flattening) — the caller reads it off the decoded state and passes it here.
     */
    Chest(Direction direction, Map<ItemType, Integer> contents, int speedLevel, BuildingPrototype prototype) {
        this(direction, contents, BuildingStatus.WORKING, speedLevel, prototype);
    }

    /**
     * The general form the restore constructor above, {@link #rotatedClockwise} and {@link
     * #withSpeedLevel} all use — the latter two pass the CURRENT {@link #status}/{@code speedLevel}
     * through (code review finding): unlike a save/load restore, neither creates a new logical
     * chest, so a chest that was actually {@code OUTPUT_FULL} must not flash back to {@code WORKING}
     * — even for one tick — just because the player rotated or upgraded it. {@code
     * HudRenderer.alerts()} reads {@code World.statusCounts()} directly now, so a stale reset here
     * is directly visible on the HUD.
     */
    private Chest(Direction direction, Map<ItemType, Integer> contents, BuildingStatus status, int speedLevel,
            BuildingPrototype prototype) {
        this.direction = direction;
        this.contents.putAll(contents);
        for (int quantity : this.contents.values()) {
            storedCount += quantity;
        }
        this.status = status;
        this.speedLevel = speedLevel;
        this.prototype = prototype;
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (totalCount() >= effectiveCapacity(world)) {
            return false;
        }
        contents.merge(item, 1, Integer::sum);
        storedCount++;
        arrivedThisTick = true;
        return true;
    }

    @Override
    public void clearArrivalMark() {
        arrivedThisTick = false;
    }

    /**
     * Runs {@link #tickOnce} {@code 1 << speedLevel} times — the same multiplier {@code
     * SpeedModule} used to produce by nesting {@code speedLevel} independent wrapper layers, each
     * doubling whatever it wrapped (owner decision: preserve the exact ×2^N stacking, not switch to
     * a linear ×(1+N) just because the mechanism moved from a decorator to a field).
     */
    @Override
    public void tick(TickContext world, int x, int y) {
        for (int i = 0, repeats = 1 << speedLevel; i < repeats; i++) {
            tickOnce(world, x, y);
        }
    }

    /** Push one stored item (whichever kind comes first in {@code rawId} order) out through {@link #direction}. */
    private void tickOnce(TickContext world, int x, int y) {
        // At capacity is a real problem worth surfacing (F-01, DEV_TASKS.md, §2.5 of the audit's
        // own "заполненный ящик — тоже OUTPUT_FULL" note) — computed here, not in accept()/appearance(),
        // since only tick() has both the current contents AND TickContext (for BIG_BUFFER) at once.
        if (arrivedThisTick) {
            status = totalCount() >= effectiveCapacity(world) ? BuildingStatus.OUTPUT_FULL : BuildingStatus.WORKING;
            return;
        }
        // Sorted snapshot once per push attempt: HashMap iteration order is not deterministic, and
        // a TreeMap here measurably regressed the belt/chest benchmark (see field javadoc).
        List<ItemType> kinds = new ArrayList<>(contents.keySet());
        kinds.sort(null);
        for (ItemType item : kinds) {
            if (world.offerForward(x + direction.dx(), y + direction.dy(), item)) {
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

    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        List<String> lines = new ArrayList<>();
        List<ItemType> kinds = new ArrayList<>(contents.keySet());
        kinds.sort(null);
        for (ItemType item : kinds) {
            lines.add("  " + item.label() + ": " + contents.getOrDefault(item, 0));
        }
        if (lines.isEmpty()) {
            lines.add("  (empty)");
        }
        return lines;
    }

    private void decrement(ItemType item) {
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
    public int amount(ItemType item) {
        return contents.getOrDefault(item, 0);
    }

    /**
     * A snapshot of everything stored, by kind — for demolition refund ({@code RemoveAction}) and
     * hand-collection ({@code GrabChestAction}). A live bug report: before these existed, a
     * chest's contents and the player's own buildable stock were two completely disconnected
     * pools — a factory that had produced plenty could still be unable to afford its own next
     * building, and demolishing a full chest silently destroyed everything inside it.
     */
    public Map<ItemType, Integer> contents() {
        return Map.copyOf(contents);
    }

    /** Empty this chest completely, returning what was taken — see {@link #contents()}'s javadoc. */
    public Map<ItemType, Integer> drain() {
        Map<ItemType, Integer> taken = Map.copyOf(contents);
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
    public void restore(Map<ItemType, Integer> items) {
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
    public boolean canRestore(TickContext world, Map<ItemType, Integer> items) {
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
        return world.research().hasEffect(VanillaTechEffects.BIG_BUFFER) ? CAPACITY * 2 : CAPACITY;
    }

    /** The status field this archetype already keeps, handed over without building an {@link Appearance} — see {@link Building#status()}. */
    @Override
    public BuildingStatus status() {
        return status;
    }

    @Override
    public Appearance appearance() {
        int total = totalCount();
        return total > 0 ? Appearance.of(prototype.texture(), total, status) : Appearance.of(prototype.texture(), status);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Chest(direction.rotate(), contents, status, speedLevel, prototype));
    }

    @Override
    public int speedLevel() {
        return speedLevel;
    }

    @Override
    public Building withSpeedLevel(int newSpeedLevel) {
        return new Chest(direction, contents, status, newSpeedLevel, prototype);
    }


    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    /**
     * Same pass selection as {@link Belt}: LEFT/UP chests must tick in the ascending pass so the
     * downstream cell pushes before the upstream one fills it. Without this, {@link
     * SettlesEachTick} alone halves LEFT/UP chest-chain throughput — the upstream cell ticks first
     * in the descending pass, marks the neighbor, and that neighbor skips its push for the tick.
     */
    @Override
    public boolean prefersDescendingTick() {
        return direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    @Override
    public ChestState state() {
        return new ChestState(direction, contents, speedLevel);
    }
}
