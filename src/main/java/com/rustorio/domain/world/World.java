package com.rustorio.domain.world;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Research;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.Tech;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.building.TickContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The play field: where ore lies and which building sits on which cell.
 *
 * <p>All building kinds live in one map, keyed by cell position, and go through one tick
 * loop, one draw traversal, one "is this cell free" check — the common {@link Building} type is
 * what makes that possible. Where sorts genuinely differ is kept out of {@code World} entirely:
 * placement rules and construction both live in {@link BuildingFactory} plus the thin per-type
 * {@code place*} wrappers below; {@code World} itself never asks "what kind of building is this."
 *
 * <p><b>Known backlog item:</b> as the aggregate root, this class is still where placement,
 * ticking, belt-segment wiring, and production/research bookkeeping all converge — it's grown
 * into the busiest class in the domain. No split is planned yet; noted here so growth stays a
 * conscious tradeoff rather than an unnoticed drift.
 */
public final class World implements TickContext {

    /**
     * A cell's position, and the {@link TreeMap} key {@link #buildings} is ordered by — {@link
     * #compareTo} sorts by {@code x} first, then {@code y}, exactly matching the packed-{@code
     * long} ordering this replaced ({@code (x << 32) | y}), without the bit arithmetic: a record
     * is precisely the tool Java added for "small compound value used as a key."
     *
     * <p>Public, not package-private (X-03, DEV_TASKS.md widened what P3-03, BUG_FIX_PROGRESS.md
     * started): {@code RotateAction}/{@code RemoveAction} in {@code com.rustorio.domain.action}
     * need it too, to resolve a click anywhere on a multi-cell building back to the ANCHOR cell
     * before calling {@link #removeBuilding}/{@link #restoreBuilding} — see {@link #originOf}.
     */
    public record Coord(int x, int y) implements Comparable<Coord> {
        @Override
        public int compareTo(Coord other) {
            int byX = Integer.compare(x, other.x);
            return byX != 0 ? byX : Integer.compare(y, other.y);
        }
    }

    private final int width;
    private final int height;
    private final BuildingFactory buildingFactory;

    /**
     * Cell to the building on it. A {@link TreeMap}, not a plain hash map, deliberately: {@link
     * #tick()} walks buildings in key order, giving a predictable order by {@code x} — see {@link
     * #tick()} for why that predictability matters to belts.
     */
    private final NavigableMap<Coord, Building> buildings = new TreeMap<>();

    /**
     * Every cell a building physically occupies, mapped to that building's ANCHOR cell (the same
     * key it's stored under in {@link #buildings}) — including the anchor cell itself, mapped to
     * itself, so cell-level lookups ({@link #isFree}, {@link #peek}, {@link #offer}) never need to
     * special-case "is this the anchor or a secondary cell" (X-03, DEV_TASKS.md).
     *
     * <p>For every 1x1 building (everything except {@code ASSEMBLER} — see {@link
     * Building#footprintWidth}) this holds EXACTLY the same entries {@link #buildings} does, one
     * cell mapped to itself; a multi-cell building adds its extra cells here without adding extra
     * entries to {@link #buildings}, so {@link #tick}/{@link #forEachBuilding} still visit it
     * exactly once, at its anchor.
     */
    private final Map<Coord, Coord> occupancy = new HashMap<>();

    /**
     * Every building's status as of its last tick (or its placement, for a building that hasn't
     * ticked yet) — the bookkeeping {@link #trackStatus} needs to tell "still {@code WORKING}"
     * from "just became {@code WORKING}" without re-deriving every building's status from scratch
     * (S1, CODE_REVIEW_2026-07-28.md). See {@link #statusCounts} for what this maintains.
     */
    private final Map<Coord, BuildingStatus> lastStatus = new HashMap<>();

    /**
     * How many buildings currently sit in each {@link BuildingStatus} — kept incrementally by
     * {@link #trackStatus} (called once per building per tick, piggy-backing on the walk {@link
     * TickScheduler} already does — no extra traversal) plus placement/removal, instead of being
     * re-derived by scanning every building (S1, CODE_REVIEW_2026-07-28.md: {@code HudRenderer}
     * used to do exactly that scan once per RENDER FRAME, not once per simulation tick, on top of
     * allocating an {@link com.rustorio.domain.Appearance} per building to read one field off it).
     * {@link #statusCounts()} hands the HUD the finished numbers; it never walks {@link #buildings}
     * itself anymore.
     */
    private final Map<BuildingStatus, Integer> statusCounts = new EnumMap<>(BuildingStatus.class);

    /** Total items ever produced — survives individual buildings being demolished. */
    private final ProductionStats stats = new ProductionStats();

    /** Research points and unlocked technologies — also survives individual demolitions. */
    private final Research research = new Research();

    /**
     * What the player has to spend on construction (D-03, DEV_TASKS.md) — see {@link
     * #trySpendBuildingCost}/{@link #refundBuildingCost}, the only doors {@link
     * com.rustorio.domain.action.PlaceAction}/{@code RemoveAction} reach it through.
     */
    private final PlayerInventory inventory = new PlayerInventory();

    /**
     * What a new game starts with — enough to place a miner, a belt, a furnace and a chest with a
     * little to spare, not enough to build a whole line outright. Not derived from anything in the
     * design audit (which names no starting-stock number); a bootstrap problem the risk section of
     * D-03 explicitly calls out (no starting stock means the very first miner is unbuildable).
     */
    private static final Map<Item, Integer> STARTING_INVENTORY = Map.of(Item.IRON_PLATE, 30);

    /**
     * Who wants to hear about every produced item. Statistics is always subscribed, but the list
     * isn't wired to it by name — another listener can sit alongside it without touching this
     * class at all.
     */
    private final List<ProductionListener> productionListeners = new ArrayList<>();

    /**
     * The world's own clock (D-06, DEV_TASKS.md) — incremented once per {@link #tick()}, never
     * read from wall-clock time. This is the number handed to every {@link ProductionListener};
     * see that interface's javadoc for why ticks, not seconds. Starts at 0 (no tick has happened
     * yet) and becomes 1 for whatever gets produced during the very first {@link #tick()} call.
     */
    private long tickCount = 0;

    /**
     * Per-cell cooldown for {@link #tryManualMine} — a live bug report, not a DEV_TASKS.md card: a
     * factory that's fully backed up (every producer stalled on {@code OUTPUT_FULL}/{@code
     * NO_FUEL}) with zero spendable {@code IRON_PLATE} had no way forward at all — demolishing
     * something refunds its cost (D-03), but a brand-new player has no reason to know that. This
     * gives an always-available way to gather raw ore by hand, no building required.
     *
     * <p>{@code HashMap}, not {@code TreeMap}/{@code EnumMap}: nothing ever iterates this map, only
     * looks up one {@link Coord} at a time, so the determinism discipline the rest of this class
     * applies to iterated collections (see {@code Coord}'s own javadoc, {@code
     * ProductionStats.Snapshot}) doesn't apply here — insertion order is simply never observed.
     * Deliberately NOT part of {@link com.rustorio.persistence.WorldSnapshot}: a save/load losing
     * track of exactly which cells are on cooldown is a harmless, momentary inconsistency (worst
     * case, a cell hand-minable a few ticks early after a reload), not a correctness issue worth a
     * new save-format field for.
     */
    private final Map<Coord, Long> manualMineReadyAtTick = new HashMap<>();

    /**
     * How long a cell stays on cooldown after a hand-mine, in ticks — 90 at the game's fixed 60
     * ticks/second step (P4-06, BUG_FIX_PROGRESS.md), i.e. 1.5 real seconds. Deliberately close to
     * a {@link com.rustorio.domain.building.Miner}'s own un-teched pace (3-tick batches, but a
     * miner also has zero travel time and never gets tired): hand-mining is a real fallback, not a
     * faster way to bootstrap a whole factory than just building a miner.
     */
    private static final long MANUAL_MINE_COOLDOWN_TICKS = 90;

    public World(int width, int height) {
        this(width, height, BuildingFactory.standard());
    }

    public World(int width, int height, BuildingFactory buildingFactory) {
        this.width = width;
        this.height = height;
        this.buildingFactory = buildingFactory;
        productionListeners.add(stats);
        STARTING_INVENTORY.forEach(inventory::add);
    }

    /** Subscribe an independent listener to "item produced" — in addition to statistics, not instead of it. */
    public void addProductionListener(ProductionListener listener) {
        productionListeners.add(listener);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The factory this world builds and restores buildings through — reused by the persistence layer. */
    public BuildingFactory buildingFactory() {
        return buildingFactory;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Whether the cell holds no building at all — including any cell of a multi-cell building, not just its anchor. */
    public boolean isFree(int x, int y) {
        return !occupancy.containsKey(new Coord(x, y));
    }

    /** The multi-cell form of {@link #isFree}: whether every cell in the {@code w}×{@code h} rectangle anchored at {@code (x, y)} is free. */
    public boolean footprintFree(int x, int y, int w, int h) {
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                if (!isFree(x + dx, y + dy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The anchor cell of the building occupying {@code (x, y)} — itself for a single-cell
     * building, or the origin (top-left) cell of a multi-cell building spanning it. Empty if the
     * cell is free. {@code RotateAction}/{@code RemoveAction} resolve through this BEFORE calling
     * {@link #removeBuilding}/{@link #restoreBuilding}, so clicking ANY cell of a multi-cell
     * building acts on the whole thing and restores it to its ORIGINAL position on undo, instead
     * of wherever happened to be clicked (X-03, DEV_TASKS.md).
     */
    public Optional<Coord> originOf(int x, int y) {
        return Optional.ofNullable(occupancy.get(new Coord(x, y)));
    }

    /** Thin one-line wrappers over {@link #place} — kept for call-site readability (tests, {@code Benchmark}). */
    public boolean placeMiner(int x, int y) {
        return place(BuildingType.MINER, x, y);
    }

    public boolean placeChest(int x, int y) {
        return place(BuildingType.CHEST, x, y);
    }

    public boolean placeFurnace(int x, int y, Direction direction) {
        return place(BuildingType.FURNACE, x, y, direction);
    }

    public boolean placePress(int x, int y, Direction direction) {
        return place(BuildingType.PRESS, x, y, direction);
    }

    public boolean placeBelt(int x, int y, Direction direction) {
        return place(BuildingType.BELT, x, y, direction);
    }

    public boolean placeUndergroundIn(int x, int y, Direction direction) {
        return place(BuildingType.UNDERGROUND_IN, x, y, direction);
    }

    public boolean placeUndergroundOut(int x, int y, Direction direction) {
        return place(BuildingType.UNDERGROUND_OUT, x, y, direction);
    }

    public boolean placeSplitter(int x, int y, Direction direction) {
        return place(BuildingType.SPLITTER, x, y, direction);
    }

    public boolean placeFilter(int x, int y, Direction direction) {
        return place(BuildingType.FILTER, x, y, direction);
    }

    public boolean placeInserter(int x, int y, Direction direction) {
        return place(BuildingType.INSERTER, x, y, direction);
    }

    public boolean placeLab(int x, int y) {
        return place(BuildingType.LAB, x, y);
    }

    /**
     * Place a building of the chosen kind, facing {@code direction} — the one place construction
     * actually happens. The cell must be free and in bounds (universal, checked here), and satisfy
     * the kind's own extra precondition, if it has one — see {@link BuildingFactory#canPlace} and
     * {@link PlacementRule}. A belt additionally joins the segment its same-direction neighbors (if
     * any) belong to — see {@link BuildingFactory#attachBelt}. Collapses what used to be nine
     * separate {@code place*} methods and a {@code switch} dispatching between them (P3-04,
     * BUG_FIX_PROGRESS.md).
     */
    public boolean place(BuildingType type, int x, int y, Direction direction) {
        // Constructed before the footprint check below can run at all: footprint size lives on
        // the BUILDING instance (Building#footprintWidth/Height), not on BuildingType, and a
        // throwaway Furnace/Miner/etc. costs nothing to build-and-discard on a failed placement —
        // no shared mutable state, no I/O (X-03, DEV_TASKS.md).
        Building building = buildingFactory.create(type, direction);
        int w = building.footprintWidth();
        int h = building.footprintHeight();
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!inBounds(cx, cy) || !isFree(cx, cy) || !buildingFactory.canPlace(type, cx, cy)) {
                    return false;
                }
            }
        }
        Coord anchor = new Coord(x, y);
        buildings.put(anchor, building);
        occupyFootprint(anchor, w, h);
        trackStatus(anchor, building);
        if (building instanceof Belt belt) {
            attachToSegment(belt, x, y, direction);
        }
        return true;
    }

    /**
     * Record {@code building}'s CURRENT status against whatever {@link #lastStatus} last saw for
     * this cell, adjusting {@link #statusCounts} by exactly the difference — a no-op if the status
     * hasn't changed. Called once at placement/restoration (the building's first status) and once
     * per building per tick from {@link TickScheduler} (S1, CODE_REVIEW_2026-07-28.md); {@link
     * #removeBuilding} does the matching decrement itself, since there's no new status to record.
     */
    private void trackStatus(Coord coord, Building building) {
        BuildingStatus status = building.appearance().status();
        BuildingStatus previous = lastStatus.put(coord, status);
        if (previous == status) {
            return;
        }
        if (previous != null) {
            statusCounts.merge(previous, -1, Integer::sum);
        }
        statusCounts.merge(status, 1, Integer::sum);
    }

    /** Reserve every cell of a {@code w}×{@code h} footprint anchored at {@code anchor} in {@link #occupancy} — see that field's own javadoc. */
    private void occupyFootprint(Coord anchor, int w, int h) {
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                occupancy.put(new Coord(anchor.x() + dx, anchor.y() + dy), anchor);
            }
        }
    }

    /** The inverse of {@link #occupyFootprint} — frees every cell of the given footprint. */
    private void freeFootprint(Coord anchor, int w, int h) {
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                occupancy.remove(new Coord(anchor.x() + dx, anchor.y() + dy));
            }
        }
    }

    /** Same, with the default direction — convenient for buildings that ignore it anyway. */
    public boolean place(BuildingType type, int x, int y) {
        return place(type, x, y, Direction.RIGHT);
    }

    private void attachToSegment(Belt belt, int x, int y, Direction direction) {
        Belt behind = beltNeighbor(x - direction.dx(), y - direction.dy(), direction).orElse(null);
        Belt ahead = beltNeighbor(x + direction.dx(), y + direction.dy(), direction).orElse(null);
        BuildingFactory.attachBelt(belt, behind, ahead);
    }

    /** The neighbor at {@code (x, y)}, if it's a belt facing the same direction — else empty. */
    private Optional<Belt> beltNeighbor(int x, int y, Direction direction) {
        Building neighbor = buildings.get(new Coord(x, y));
        if (neighbor == null) {
            return Optional.empty();
        }
        return (Building.unwrap(neighbor) instanceof Belt belt && belt.direction() == direction)
                ? Optional.of(belt)
                : Optional.empty();
    }

    /**
     * Demolish the building on a cell and return it, if there was one. Returns the SAME object
     * that stood there (not a fresh equivalent) — {@code UndoAction} needs to be able to put back
     * exactly what was demolished, buffers and all, not a blank replacement.
     */
    public Optional<Building> removeBuilding(int x, int y) {
        Coord anchor = occupancy.get(new Coord(x, y));
        if (anchor == null) {
            return Optional.empty();
        }
        Building removed = buildings.remove(anchor);
        if (removed == null) {
            return Optional.empty(); // occupancy and buildings are always kept in sync — defensive only
        }
        freeFootprint(anchor, removed.footprintWidth(), removed.footprintHeight());
        BuildingStatus lastKnown = lastStatus.remove(anchor);
        if (lastKnown != null) {
            statusCounts.merge(lastKnown, -1, Integer::sum);
        }
        if (Building.unwrap(removed) instanceof Belt belt) {
            BuildingFactory.detachBelt(belt);
        }
        return Optional.of(removed);
    }

    /**
     * Place an ALREADY-BUILT building into a cell, with none of {@link #place}'s checks. Used by
     * the persistence layer and by undo/redo: the placement rules were already satisfied when the
     * building was first built (or first loaded); re-checking a past decision serves no purpose.
     *
     * <p>Idempotent with respect to belt segments: a belt is always detached from whatever segment
     * it currently sits in before being reattached. Callers like {@code UpgradeSpeedAction.undo}
     * pass back a {@link Belt} that was never removed from its segment in the first place (only
     * wrapped in a {@link SpeedModule}); without this, {@code attachToSegment} would add it a
     * second time, corrupting the segment's tile list.
     */
    public void restoreBuilding(int x, int y, Building building) {
        Coord anchor = new Coord(x, y);
        buildings.put(anchor, building);
        occupyFootprint(anchor, building.footprintWidth(), building.footprintHeight());
        trackStatus(anchor, building);
        if (Building.unwrap(building) instanceof Belt belt) {
            BuildingFactory.detachBelt(belt);
            attachToSegment(belt, x, y, belt.direction());
        }
    }

    /**
     * Demolish every building and reset statistics/research — the world is ready for a fresh load.
     *
     * <p>Also empties the player's inventory rather than reseeding {@link #STARTING_INVENTORY}:
     * this state isn't part of {@code WorldSnapshot} yet (D-07 adds it), so the honest thing to do
     * until then is "unknown, so nothing" — reseeding the starting stock here would let F9 (load)
     * double as a free-resources exploit.
     */
    public void clear() {
        buildings.clear();
        occupancy.clear();
        lastStatus.clear();
        statusCounts.clear();
        stats.clear();
        research.clear();
        inventory.clear();
        // Not part of WorldSnapshot (see manualMineReadyAtTick's own javadoc), but MUST still be
        // wiped here (code review finding): a LOAD immediately follows clear() with
        // restoreTickCount, which can move the clock BACKWARD (loading an earlier save after
        // playing on). Leaving stale entries in place then compares the OLD, higher cooldown
        // deadlines against the newly-lowered tickCount, refusing hand-mining on those cells for
        // however far back the clock just moved — far more than the "a few ticks early" this
        // field's own javadoc promises.
        manualMineReadyAtTick.clear();
        // Back to a fresh game's clock. A LOAD immediately follows this with restoreTickCount
        // (N3, NEW_BUGS_PROGRESS.md), so only an actual reset ends up starting from zero.
        tickCount = 0;
    }

    /**
     * One world step: every building lives its tick, knowing where it stands. The ordering rules
     * (and the P2-07 cross-segment gap they used to leave open) live in {@link TickScheduler} now
     * — see its javadoc — extracted out of {@code World} in P3-03, BUG_FIX_PROGRESS.md.
     */
    public void tick() {
        tickCount++;
        TickScheduler.tick(buildings, this, this::trackStatus);
    }

    /**
     * How many buildings currently sit in each {@link BuildingStatus} — read-only, unmodifiable
     * (S1, CODE_REVIEW_2026-07-28.md). {@code HudRenderer}'s alerts line reads this directly
     * instead of scanning every building itself; see {@link #statusCounts} the field for how it's
     * kept current.
     *
     * <p>A live view ({@code Collections.unmodifiableMap}), not a defensive copy (code review
     * finding): {@code HudRenderer.alerts()} calls this every frame BEFORE its own cheap
     * signature-based cache check even runs, so a {@code Map.copyOf} here — a fresh {@code
     * EnumMap}-sized allocation every single frame — undercut exactly the cost that cache exists
     * to avoid. Same "read-only view over live state, not a snapshot" contract {@link #stats()}/
     * {@link #inventory()}/{@link #research()} already use; safe here for the same reason it's safe
     * there — read once per call, never held across a mutation, unlike {@code Chest#contents()},
     * which deliberately DOES need a decoupled point-in-time snapshot for undo.
     */
    public Map<BuildingStatus, Integer> statusCounts() {
        return Collections.unmodifiableMap(statusCounts);
    }

    /** This world's own tick counter — see {@link #tickCount} and {@link ProductionListener}. */
    public long currentTick() {
        return tickCount;
    }

    /**
     * Resume the clock from a save file (N3, NEW_BUGS_PROGRESS.md) — see {@code JsonSaveRepository}.
     * Until this existed, {@link #clear} zeroed {@link #tickCount} on every load while {@link
     * ProductionStats}, whose entries are timestamped in ticks, was restored in full: the stats came
     * back describing a timeline the clock underneath them no longer agreed with, and every
     * derived rate (items per minute) read from a clock that had just restarted.
     */
    public void restoreTickCount(long ticks) {
        tickCount = ticks;
    }

    /** Tell every {@link ProductionListener} an item was produced — once per finished batch. */
    @Override
    public void notifyProduced(Item item) {
        for (ProductionListener listener : productionListeners) {
            listener.onProduced(tickCount, item);
        }
    }

    /**
     * Hand an item to whatever building sits at a cell, if it's willing.
     *
     * <p>The world never enumerates building sorts through {@code instanceof} here — it asks
     * {@link Building#accept} and the building answers for itself.
     *
     * <p><b>Scope note (X-03, DEV_TASKS.md).</b> Resolving {@code (x, y)} through {@link
     * #occupancy} means a multi-cell building (currently only {@code ASSEMBLER}) accepts an item
     * offered to ANY of its occupied cells, not only a specific input side — {@link
     * com.rustorio.domain.building.Furnace#accept} doesn't look at position at all, so this is a
     * deliberate simplification, not an oversight: a real per-side port system is out of scope for
     * "make World support multi-cell buildings at all," the same kind of documented scope call
     * X-01's {@code Inserter} javadoc already makes for its own mechanic.
     */
    private boolean offer(int x, int y, Item item) {
        Coord anchor = occupancy.get(new Coord(x, y));
        Building building = anchor == null ? null : buildings.get(anchor);
        return building != null && building.accept(this, item);
    }

    /**
     * Hand an item to ONE specific neighbor, addressed by direction — never broadcast to whichever
     * of the four happens to accept. Every producer ({@link com.rustorio.domain.building.Miner},
     * {@link com.rustorio.domain.building.Furnace}, {@link Belt}) holds a finished item and retries
     * delivery every tick until it succeeds; "produced" (once, at the moment of completion) and
     * "delivered" (however many retries it takes) are deliberately different events, or statistics
     * would credit output that never happened.
     */
    @Override
    public boolean offerForward(int x, int y, Item item) {
        return offer(x, y, item);
    }

    /**
     * Look at a cell without offering it anything or consuming anything — unlike {@link #offer}.
     * Needed by tunnel entrances searching for their exit partner ahead.
     */
    @Override
    public Optional<Building> peek(int x, int y) {
        Coord anchor = occupancy.get(new Coord(x, y));
        return anchor == null ? Optional.empty() : Optional.ofNullable(buildings.get(anchor));
    }

    /** Read-only view — see {@link ProductionStatsView} for why this isn't {@code ProductionStats} itself. */
    public ProductionStatsView stats() {
        return stats;
    }

    /** Read-only view — see {@link PlayerInventoryView} for why this isn't {@code PlayerInventory} itself. */
    public PlayerInventoryView inventory() {
        return inventory;
    }

    /**
     * Spend {@code type}'s {@link BuildingCost} from the player's inventory — {@code false} (and
     * nothing spent) if they can't afford it. The one door {@link
     * com.rustorio.domain.action.PlaceAction} reaches the inventory through to charge for a
     * placement, and {@code RemoveAction.undo} to re-charge an undone demolition.
     */
    public boolean trySpendBuildingCost(BuildingType type) {
        BuildingCost cost = BuildingCost.forType(type);
        return inventory.trySpend(cost.item(), cost.amount());
    }

    /**
     * Credit {@code type}'s {@link BuildingCost} back to the player's inventory — the inverse of
     * {@link #trySpendBuildingCost}, used by demolition, by undoing a placement, and (per D-03's
     * own acceptance criterion) never refused: crediting resources back can't fail the way spending
     * them can.
     */
    public void refundBuildingCost(BuildingType type) {
        BuildingCost cost = BuildingCost.forType(type);
        inventory.add(cost.item(), cost.amount());
    }

    /**
     * Credit an arbitrary amount of one item to the player's inventory — the general form of
     * {@link #refundBuildingCost}, not tied to any one {@link BuildingType}'s cost. Used when
     * demolishing a {@link com.rustorio.domain.building.Chest} returns its contents ({@code
     * RemoveAction}) and when hand-collecting a chest's contents outright ({@code
     * GrabChestAction}) — a live bug report: a chest's contents and the player's own buildable
     * stock used to be two completely disconnected pools.
     */
    public void creditItem(Item item, int amount) {
        inventory.add(item, amount);
    }

    /**
     * Atomically spend several items at once — either every one requested is available and gets
     * deducted, or nothing changes. The multi-item form of {@link #trySpendBuildingCost}'s own
     * atomicity, used by {@code RemoveAction}/{@code GrabChestAction}'s undo to claw back exactly
     * what a demolition or a hand-collection credited — the same "can't afford to undo, stays
     * applied" compromise {@link #trySpendBuildingCost} already makes for a single item.
     */
    public boolean trySpendItems(Map<Item, Integer> items) {
        return inventory.trySpendAll(items);
    }

    /**
     * Reach directly into an empty ore cell and take one unit by hand, straight into inventory —
     * see {@link #manualMineReadyAtTick}'s javadoc for why this exists. Goes through the exact
     * same {@link com.rustorio.domain.OreLayout#extract} a real {@link
     * com.rustorio.domain.building.Miner} would call, so it drains the same finite reserve
     * (D-04, DEV_TASKS.md) — this can never conjure ore a miner standing here wouldn't also have
     * found, only trade real-time attention for not having one built yet.
     *
     * @return the item taken, or empty if the cell is occupied, has no ore, or is still on cooldown
     */
    public Optional<Item> tryManualMine(int x, int y) {
        if (!inBounds(x, y) || !isFree(x, y)) {
            return Optional.empty();
        }
        Coord coord = new Coord(x, y);
        if (tickCount < manualMineReadyAtTick.getOrDefault(coord, 0L)) {
            return Optional.empty();
        }
        Optional<Item> ore = buildingFactory.oreLayout().extract(x, y);
        ore.ifPresent(item -> {
            inventory.add(item, 1);
            manualMineReadyAtTick.put(coord, tickCount + MANUAL_MINE_COOLDOWN_TICKS);
        });
        return ore;
    }

    /** Read-only view — see {@link ResearchView} for why this isn't {@code Research} itself. */
    @Override
    public ResearchView research() {
        return research;
    }

    /** Add research points (called once per finished {@code Lab} batch) — the one door for mutating research. */
    @Override
    public void addResearchPoints(int amount) {
        research.addPoints(amount);
    }

    /**
     * Spend {@code tech}'s cost to unlock it — the player's explicit choice (P-02, DEV_TASKS.md;
     * see {@link Research#unlock}). {@code false} (nothing spent, nothing unlocked) if the player
     * can't afford it, already has it, or hasn't unlocked its prerequisites yet.
     */
    public boolean tryUnlockTech(Tech tech) {
        return research.unlock(tech);
    }

    /** Overwrite research progress wholesale from a save file — see {@code JsonSaveRepository}. */
    public void restoreResearch(Research.Snapshot snapshot) {
        research.restore(snapshot);
    }

    /** Overwrite production totals wholesale from a save file — see {@code JsonSaveRepository}. */
    public void restoreStats(ProductionStats.Snapshot snapshot) {
        stats.restore(snapshot);
    }

    /** Overwrite the player's inventory wholesale from a save file (D-07, DEV_TASKS.md) — see {@code JsonSaveRepository}. */
    public void restoreInventory(PlayerInventory.Snapshot snapshot) {
        inventory.restore(snapshot);
    }

    /** Visit every building with its coordinates — used by persistence, which needs the whole map. */
    public void forEachBuilding(BuildingVisitor visitor) {
        for (Map.Entry<Coord, Building> entry : buildings.entrySet()) {
            Coord coord = entry.getKey();
            visitor.visit(coord.x(), coord.y(), entry.getValue());
        }
    }

    /**
     * Visit only the buildings within a rectangle (inclusive) — what the rendering layers actually
     * want, instead of the full {@link #forEachBuilding} walk they used to do once (twice, for
     * {@code ItemRenderer}/{@code OverlayRenderer}) per frame regardless of zoom (P4-04,
     * BUG_FIX_PROGRESS.md). Takes four raw ints, not a {@code TileRange} — that type belongs to
     * {@code com.graphics.render}, and the domain doesn't import from the rendering layer.
     *
     * <p>{@link Coord#compareTo} orders by {@code x} first, so ONE {@code subMap} from {@code
     * (minX, minY)} to {@code (maxX, maxY)} narrows the scan to the right X range but NOT to the
     * right Y range: between the two boundary X values the order says nothing about Y, so such a
     * scan walks every building in the X band and discards the ones outside {@code [minY, maxY]}.
     * Taking one {@code subMap} PER COLUMN instead (N18, NEW_BUGS_PROGRESS.md — owner decision)
     * makes the range exact: within a single X, the order IS by Y, so every entry the iteration sees
     * is one the visitor wants, and nothing has to be filtered afterward. The cost is one
     * {@code O(log n)} lookup per column of the rectangle, against a walk over buildings that are
     * never drawn — the taller and busier the map above/below the camera, the more that trade pays.
     *
     * <p><b>Known limitation (X-03, DEV_TASKS.md).</b> This filters by each building's ANCHOR cell
     * only — a multi-cell building (currently only {@code ASSEMBLER}) whose anchor sits just
     * outside {@code [minX, maxX] × [minY, maxY]} but whose footprint still overlaps it won't be
     * visited, and so won't be drawn, for up to {@code footprintWidth - 1}/{@code
     * footprintHeight - 1} tiles at the exact edge of the camera's visible range. Purely cosmetic
     * (place/demolish/rotate never go through this method) and self-corrects the moment the camera
     * moves — not worth padding every one of this method's several call sites in {@code
     * com.graphics.render} for a one-tile pop-in at the screen edge.
     */
    public void forEachBuildingIn(int minX, int minY, int maxX, int maxY, BuildingVisitor visitor) {
        if (minX > maxX || minY > maxY) {
            return; // an empty rectangle (e.g. the camera entirely off the map) — nothing to visit
        }
        for (int x = minX; x <= maxX; x++) {
            var column = buildings.subMap(new Coord(x, minY), true, new Coord(x, maxY), true);
            for (Map.Entry<Coord, Building> entry : column.entrySet()) {
                Coord coord = entry.getKey();
                visitor.visit(coord.x(), coord.y(), entry.getValue());
            }
        }
    }

    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
    }
}
