package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Cell;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Research;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.TechType;
import com.rustorio.domain.VanillaTechs;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.FluidNetwork;
import com.rustorio.domain.building.FluidNode;
import com.rustorio.domain.building.FluidPort;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.PowerNetwork;
import com.rustorio.domain.building.PowerNode;
import com.rustorio.domain.building.PowerProducer;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.TransportNode;
import com.rustorio.domain.building.VanillaBuildings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

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

    /** Cloned once here rather than per placement: {@code Direction.values()} copies the array on every call. */
    private static final Direction[] SIDES = Direction.values();

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

    /**
     * Every placed pole, by its own cell — the poles are few (dozens, against thousands of belts),
     * so the two operations that walk all of them ({@link #powerNeighbors}, rebuilding {@link
     * #powerCoverage} after a demolition) stay cheap AND happen only on a player action, never in a
     * tick.
     */
    private final NavigableMap<Coord, PowerNode> poles = new TreeMap<>();

    /**
     * A covered cell's claimant: the pole itself, plus the cell that pole stands on. The pole rather
     * than its network, because a pole always knows its CURRENT network, so merges and splits need
     * no update here at all; and the CELL alongside it because {@link #claimCoverage} has to compare
     * claimants by position, and finding a pole's cell by searching {@link #poles} for it made
     * claiming a square cost a walk over every pole on the map — quadratic in the number of poles,
     * for an answer that was already in hand at the only place that writes it.
     */
    private record Coverage(Coord poleCell, PowerNode pole) {
    }

    /**
     * Which pole covers each cell — the lookup {@link #drawPower} makes once per powered machine per
     * tick, kept as a map so that lookup is O(1) instead of a walk over every pole on the map.
     *
     * <p>Where two poles overlap, the one with the smaller cell wins — an arbitrary rule, but a
     * fixed one, so the same layout always resolves the same way.
     */
    private final Map<Coord, Coverage> powerCoverage = new HashMap<>();

    /**
     * Every placed generator, in coordinate order — walked once per {@link #tick()}, before any
     * building ticks, so a machine can never spend power that has not been generated yet. Ordered
     * because generators draw steam as they produce, and which one drains a shared pipe first has to
     * be the same on every run.
     */
    private final NavigableMap<Coord, PowerProducer> powerProducers = new TreeMap<>();

    /** Total items ever produced — survives individual buildings being demolished. */
    private final ProductionStats stats = new ProductionStats();

    /** Research points and unlocked technologies — also survives individual demolitions. */
    private final Research research;

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
    private static final Map<ItemType, Integer> STARTING_INVENTORY = Map.of(VanillaItems.IRON_PLATE, 30);

    /**
     * Who wants to hear about every produced item. Statistics is always subscribed, but the list
     * isn't wired to it by name — another listener can sit alongside it without touching this
     * class at all.
     */
    private final List<ProductionListener> productionListeners = new ArrayList<>();

    /** Who wants to hear about a building actually being placed by {@link #place} — see that method and {@link BuildingPlacedListener}'s own javadoc for what does and doesn't count. */
    private final List<BuildingPlacedListener> buildingPlacedListeners = new ArrayList<>();

    /** Who may refuse a placement before it happens — see {@link PlacementVeto}. Empty in a game with no mods, so the loop below costs nothing. */
    private final List<PlacementVeto> placementVetoes = new ArrayList<>();

    /** Who wants to hear that a world tick just finished — see {@link #tick()} and {@link TickListener}. */
    private final List<TickListener> tickListeners = new ArrayList<>();

    /** Who wants to hear that a technology was actually unlocked — see {@link #tryUnlockTech}. */
    private final List<ResearchCompleteListener> researchCompleteListeners = new ArrayList<>();

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

    /** Researches through the built-in technologies — see the 4-arg constructor for a world running a mod's own. */
    public World(int width, int height, BuildingFactory buildingFactory) {
        this(width, height, buildingFactory, VanillaTechs.frozen());
    }

    public World(int width, int height, BuildingFactory buildingFactory, Registry<TechType> techs) {
        this.width = width;
        this.height = height;
        this.buildingFactory = buildingFactory;
        this.research = new Research(techs);
        productionListeners.add(stats);
        STARTING_INVENTORY.forEach(inventory::add);
    }

    /** Subscribe an independent listener to "item produced" — in addition to statistics, not instead of it. */
    public void addProductionListener(ProductionListener listener) {
        productionListeners.add(listener);
    }

    /** Register a veto over placement — see {@link PlacementVeto}. Every registered veto must agree for a building to go up. */
    public void addPlacementVeto(PlacementVeto veto) {
        placementVetoes.add(veto);
    }

    /** Subscribe a listener to "a building was placed" — see {@link BuildingPlacedListener}'s own javadoc for exactly which calls fire it. */
    public void addBuildingPlacedListener(BuildingPlacedListener listener) {
        buildingPlacedListeners.add(listener);
    }

    /** Subscribe a listener to "a world tick just finished" — called once, at the end of every {@link #tick()}. */
    public void addTickListener(TickListener listener) {
        tickListeners.add(listener);
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
     * any) belong to — see {@link #attachToSegment}. Collapses what used to be nine
     * separate {@code place*} methods and a {@code switch} dispatching between them (P3-04,
     * BUG_FIX_PROGRESS.md).
     */
    public boolean place(ContentId prototypeId, int x, int y, Direction direction) {
        // Constructed before the footprint check below can run at all: footprint size lives on
        // the BUILDING instance (Building#footprintWidth/Height), not on the prototype id, and a
        // throwaway Furnace/Miner/etc. costs nothing to build-and-discard on a failed placement —
        // no shared mutable state, no I/O (X-03, DEV_TASKS.md).
        Building building = buildingFactory.create(prototypeId, direction);
        int w = building.footprintWidth();
        int h = building.footprintHeight();
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!inBounds(cx, cy) || !isFree(cx, cy) || !buildingFactory.canPlace(prototypeId, cx, cy)) {
                    return false;
                }
            }
        }
        // Asked once the placement is known to be legal and before anything is committed: a veto
        // that refused after the fact would leave the caller to undo a half-placed building.
        for (PlacementVeto veto : placementVetoes) {
            if (!veto.allowPlacement(prototypeId, x, y)) {
                return false;
            }
        }
        Coord anchor = new Coord(x, y);
        buildings.put(anchor, building);
        occupyFootprint(anchor, w, h);
        trackStatus(anchor, building);
        if (building instanceof TransportNode node) {
            attachToSegment(node, x, y, direction);
        }
        if (building instanceof FluidNode node) {
            attachToFluidNetwork(node, x, y);
        }
        registerPowerRoles(anchor, building);
        for (BuildingPlacedListener listener : buildingPlacedListeners) {
            listener.onBuildingPlaced(building.prototypeId(), x, y);
        }
        return true;
    }

    /** Convenience for the closed vanilla set — resolves {@code type}'s own prototype id and delegates to {@link #place(ContentId, int, int, Direction)}. */
    public boolean place(BuildingType type, int x, int y, Direction direction) {
        return place(VanillaBuildings.idFor(type), x, y, direction);
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

    private void attachToSegment(TransportNode node, int x, int y, Direction direction) {
        TransportNode behind = transportNeighbor(x - direction.dx(), y - direction.dy(), direction).orElse(null);
        TransportNode ahead = transportNeighbor(x + direction.dx(), y + direction.dy(), direction).orElse(null);
        BuildingFactory.attachTransportNode(node, behind, ahead);
    }

    /**
     * Hand a freshly placed (or restored) fluid tile to its network, along with every already-placed
     * fluid tile touching it — this class finds them (it owns the cell map), {@code BuildingFactory}'s
     * narrow door does the rest, exactly the split {@link #attachToSegment} already uses for belts.
     */
    private void attachToFluidNetwork(FluidNode node, int x, int y) {
        List<FluidNode> neighbors = new ArrayList<>(SIDES.length);
        for (Direction side : SIDES) {
            Building neighbor = buildings.get(new Coord(x + side.dx(), y + side.dy()));
            if (neighbor instanceof FluidNode fluidNeighbor && fluidNeighbor.network() != null) {
                neighbors.add(fluidNeighbor);
            }
        }
        BuildingFactory.attachFluidNode(node, x, y, neighbors);
    }

    /** The neighbor at {@code (x, y)}, if it's a transport node facing the same direction — else empty. */
    private Optional<TransportNode> transportNeighbor(int x, int y, Direction direction) {
        Building neighbor = buildings.get(new Coord(x, y));
        if (neighbor == null) {
            return Optional.empty();
        }
        return (neighbor instanceof TransportNode node && node.direction() == direction)
                ? Optional.of(node)
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
        if (removed instanceof TransportNode node) {
            BuildingFactory.detachTransportNode(node);
        }
        if (removed instanceof FluidNode node) {
            BuildingFactory.detachFluidNode(node, anchor.x(), anchor.y());
        }
        unregisterPowerRoles(anchor, removed);
        return Optional.of(removed);
    }

    /**
     * Place an ALREADY-BUILT building into a cell, with none of {@link #place}'s checks. Used by
     * the persistence layer and by undo/redo: the placement rules were already satisfied when the
     * building was first built (or first loaded); re-checking a past decision serves no purpose.
     *
     * <p>Idempotent with respect to belt segments: a belt is always detached from whatever segment
     * it currently sits in before being reattached. Callers like {@code RotateAction} pass back a
     * {@link Belt} that was never removed from its segment in the first place (only demolished and
     * immediately restored); without this, {@code attachToSegment} would add it a second time,
     * corrupting the segment's tile list.
     */
    public void restoreBuilding(int x, int y, Building building) {
        // No PlacementVeto here, deliberately, and for the same reason BuildingPlacedListener
        // doesn't fire either: restoring a save or an undone action is not a player placing
        // something. A veto that ran here would let a mod quietly delete buildings out of an
        // existing factory just by being installed.
        Coord anchor = new Coord(x, y);
        buildings.put(anchor, building);
        occupyFootprint(anchor, building.footprintWidth(), building.footprintHeight());
        trackStatus(anchor, building);
        if (building instanceof TransportNode node) {
            BuildingFactory.detachTransportNode(node);
            attachToSegment(node, x, y, node.direction());
        }
        // Same idempotence reason as the belt and fluid re-attachment below: a restore may be
        // handing back a building that was never actually taken out (RotateAction), and a pole
        // registered twice would sit in two grids at once.
        unregisterPowerRoles(anchor, building);
        registerPowerRoles(anchor, building);
        if (building instanceof FluidNode node) {
            // Detached first, for the same idempotence reason belts are: RotateAction and friends
            // hand back a tile that was never actually taken out of its network, and attaching it a
            // second time would put one tile into two networks at once. Detaching also hands the
            // tile its own share back, which the attach below immediately pours in again — so a
            // restore that changes nothing really does change nothing.
            BuildingFactory.detachFluidNode(node, x, y);
            attachToFluidNetwork(node, x, y);
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
        poles.clear();
        powerCoverage.clear();
        powerProducers.clear();
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
        // Before any building ticks: a machine must never be able to spend power that this tick's
        // generators have not produced yet — see PowerNetwork's own javadoc.
        supplyPowerNetworks();
        TickScheduler.tick(buildings, this, this::trackStatus);
        for (TickListener listener : tickListeners) {
            listener.onTick(tickCount);
        }
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
    public void notifyProduced(ItemType item) {
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
    private boolean offer(int x, int y, ItemType item) {
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
    public boolean offerForward(int x, int y, ItemType item) {
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

    /**
     * The fluid network touching {@code (x, y)} on {@code side}, if that neighbor is a fluid tile
     * at all — the fluid counterpart to {@link #offerForward}, and the one door a machine reaches
     * plumbing through. Resolved through {@link #occupancy} like every other cell lookup here, so a
     * multi-cell machine's every occupied cell can address its own sides.
     */
    @Override
    public Optional<FluidPort> fluidPort(int x, int y, Direction side) {
        Coord anchor = occupancy.get(new Coord(x + side.dx(), y + side.dy()));
        Building neighbor = anchor == null ? null : buildings.get(anchor);
        if (!(neighbor instanceof FluidNode node)) {
            return Optional.empty();
        }
        FluidNetwork network = node.network();
        return network == null ? Optional.empty() : Optional.of(network);
    }

    /**
     * The identity of the fluid network on cell {@code (x, y)} — its {@link FluidNetwork#anchor()} —
     * or empty if no fluid tile sits there or it has joined no network yet. A render-only query for
     * the network overlay, which paints every tile of one network the same colour; the fluid twin of
     * {@link #powerNetworkAt}. Resolved through {@link #occupancy} so a multi-cell tile answers on
     * any of its own cells, exactly as {@link #fluidPort} does.
     */
    public Optional<Cell> fluidNetworkAt(int x, int y) {
        FluidNetwork network = fluidNetworkObjectAt(x, y);
        return network == null ? Optional.empty() : Optional.of(network.anchor());
    }

    /**
     * Which sides of {@code (x, y)} carry a pipe joint: a set of {@link Direction#ordinal()} bits,
     * one per neighbour sharing this tile's {@link FluidNetwork} instance. Zero when the cell holds
     * no fluid tile at all, which is what almost every cell is.
     *
     * <p>One call per building per frame, not one per SIDE per building: the renderer walks every
     * visible building, and asking four separate questions meant four cell lookups — with their
     * {@code Coord} allocations — even for a belt that was never going to have a joint. Here the
     * tile's own network is resolved once and an ordinary building leaves immediately.
     *
     * <p>A packed {@code int} rather than a set or a {@code boolean[]} for the same reason: this
     * runs inside the frame loop, where allocating per building is exactly what {@code graphics.md}
     * forbids.
     *
     * <p>Membership is compared by network IDENTITY, not by anchor: two tiles are joined exactly
     * when they share the one instance, which is what keeps a water pipe and the steam pipe it abuts
     * (two networks, one border) correctly un-jointed.
     */
    public int fluidJoints(int x, int y) {
        FluidNetwork here = fluidNetworkObjectAt(x, y);
        if (here == null) {
            return 0;
        }
        int joints = 0;
        for (Direction side : SIDES) {
            if (here == fluidNetworkObjectAt(x + side.dx(), y + side.dy())) {
                joints |= 1 << side.ordinal();
            }
        }
        return joints;
    }

    /** Whether {@link #fluidJoints} says a joint runs toward {@code side} — the bit test spelled out, so callers never repeat the shift. */
    public static boolean hasJoint(int joints, Direction side) {
        return (joints & (1 << side.ordinal())) != 0;
    }

    /** The {@link FluidNetwork} instance on cell {@code (x, y)}, or null if it holds no fluid tile or none joined yet. */
    private @Nullable FluidNetwork fluidNetworkObjectAt(int x, int y) {
        Coord anchor = occupancy.get(new Coord(x, y));
        Building here = anchor == null ? null : buildings.get(anchor);
        return here instanceof FluidNode node ? node.network() : null;
    }

    /**
     * Pay for one machine's tick out of the grid covering its cell — see {@link
     * TickContext#drawPower}. A cell no pole reaches has no grid and so no power, which is the
     * ordinary case for the entire map until somebody builds one.
     */
    @Override
    public boolean drawPower(int x, int y, long amount) {
        Coverage covering = powerCoverage.get(new Coord(x, y));
        if (covering == null) {
            return false;
        }
        PowerNetwork network = covering.pole().network();
        return network != null && network.draw(amount, tickCount);
    }

    /**
     * The identity of the power grid covering cell {@code (x, y)} — its {@link PowerNetwork#anchor()}
     * — or empty if no pole reaches it. A render-only query for the network overlay; uses the same
     * {@link #powerCoverage} lookup as {@link #drawPower}, so a cell is "on the grid" here exactly
     * when a machine standing on it could draw power.
     */
    public Optional<Cell> powerNetworkAt(int x, int y) {
        Coverage covering = powerCoverage.get(new Coord(x, y));
        if (covering == null) {
            return Optional.empty();
        }
        PowerNetwork network = covering.pole().network();
        return network == null ? Optional.empty() : Optional.of(network.anchor());
    }

    /**
     * Fill every grid's pool from its generators — run at the top of {@link #tick()}, before any
     * building ticks, so that {@link #drawPower} during this tick can only ever hand out power that
     * was actually generated during it. A generator not covered by any pole produces nothing at all
     * rather than producing into the void: it is asked only when there is a grid to ask on behalf of.
     */
    private void supplyPowerNetworks() {
        for (Map.Entry<Coord, PowerProducer> entry : powerProducers.entrySet()) {
            Coord at = entry.getKey();
            Coverage covering = powerCoverage.get(at);
            if (covering == null) {
                continue;
            }
            PowerNetwork network = covering.pole().network();
            if (network == null) {
                continue; // defensive: a placed pole always has a network
            }
            network.contribute(entry.getValue().produce(this, at.x(), at.y()), tickCount);
        }
    }

    /**
     * Take note of whatever electrical role a freshly placed (or restored) building has, if any:
     * a pole joins the grids around it, a generator joins the list {@link #supplyPowerNetworks}
     * walks. A building that is neither — almost every building — costs one failed {@code
     * instanceof} and nothing else.
     */
    private void registerPowerRoles(Coord anchor, Building building) {
        if (building instanceof PowerNode pole) {
            poles.put(anchor, pole);
            attachToPowerNetwork(pole, anchor.x(), anchor.y());
        }
        if (building instanceof PowerProducer producer) {
            powerProducers.put(anchor, producer);
        }
    }

    /** The inverse of {@link #registerPowerRoles} — a demolished pole leaves its grid, and its coverage is re-resolved from the poles that remain. */
    private void unregisterPowerRoles(Coord anchor, Building building) {
        if (building instanceof PowerNode pole) {
            poles.remove(anchor);
            BuildingFactory.detachPowerNode(pole, anchor.x(), anchor.y(), this::polesConnect);
            rebuildPowerCoverage();
        }
        if (building instanceof PowerProducer) {
            powerProducers.remove(anchor);
        }
    }

    /** Whether the poles standing on two cells can see each other — what {@code PowerNetwork} asks while re-splitting a grid. */
    private boolean polesConnect(Cell one, Cell other) {
        PowerNode first = poles.get(new Coord(one.x(), one.y()));
        PowerNode second = poles.get(new Coord(other.x(), other.y()));
        if (first == null || second == null) {
            return false;
        }
        int gap = Math.max(Math.abs(one.x() - other.x()), Math.abs(one.y() - other.y()));
        return gap <= Math.max(first.coverageRadius(), second.coverageRadius());
    }

    /**
     * Wire a freshly placed pole into the grids around it, and claim the cells it now covers.
     * {@link #powerNeighbors} walks every pole on the map — poles are counted in dozens and this is
     * a player action, so the simple answer is the right one here.
     */
    private void attachToPowerNetwork(PowerNode node, int x, int y) {
        BuildingFactory.attachPowerNode(node, x, y, powerNeighbors(x, y, node.coverageRadius()));
        claimCoverage(new Coord(x, y), node);
    }

    /** Every already-placed pole close enough to connect — either pole's own radius reaching the other is enough, so a big pole is worth building. */
    private List<PowerNode> powerNeighbors(int x, int y, int radius) {
        List<PowerNode> neighbors = new ArrayList<>();
        for (Map.Entry<Coord, PowerNode> entry : poles.entrySet()) {
            Coord at = entry.getKey();
            if (at.x() == x && at.y() == y) {
                continue;
            }
            int gap = Math.max(Math.abs(at.x() - x), Math.abs(at.y() - y));
            if (gap <= Math.max(radius, entry.getValue().coverageRadius())) {
                neighbors.add(entry.getValue());
            }
        }
        return neighbors;
    }

    /**
     * Write {@code pole} into every cell of its coverage, unless a pole standing on a smaller cell
     * already claimed it — see {@link #powerCoverage}. O(r²) with a constant-time test per cell:
     * the incumbent's own cell rides along in {@link Coverage}, so deciding who wins never searches
     * for it.
     */
    private void claimCoverage(Coord at, PowerNode pole) {
        int radius = pole.coverageRadius();
        Coverage claim = new Coverage(at, pole);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Coord cell = new Coord(at.x() + dx, at.y() + dy);
                Coverage existing = powerCoverage.get(cell);
                if (existing == null || at.compareTo(existing.poleCell()) < 0) {
                    powerCoverage.put(cell, claim);
                }
            }
        }
    }

    /**
     * Rebuild {@link #powerCoverage} from scratch after a pole was demolished. Rebuilding wholesale
     * rather than subtracting one pole's square is what makes overlapping coverage come out right
     * without a second bookkeeping structure to get wrong, and it costs O(poles × r²) — linear in
     * the poles, not quadratic, since {@link #claimCoverage} no longer searches for the incumbent's
     * position. Runs on a player action, never in a tick.
     */
    private void rebuildPowerCoverage() {
        powerCoverage.clear();
        for (Map.Entry<Coord, PowerNode> entry : poles.entrySet()) {
            claimCoverage(entry.getKey(), entry.getValue());
        }
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
    public boolean trySpendBuildingCost(ContentId prototypeId) {
        BuildingCost cost = buildingFactory.prototype(prototypeId).cost();
        return inventory.trySpend(cost.item(), cost.amount());
    }

    /** Convenience for the closed vanilla set — resolves {@code type}'s own prototype id and delegates to {@link #trySpendBuildingCost(ContentId)}. */
    public boolean trySpendBuildingCost(BuildingType type) {
        return trySpendBuildingCost(VanillaBuildings.idFor(type));
    }

    /**
     * Credit a prototype's {@link BuildingCost} back to the player's inventory — the inverse of
     * {@link #trySpendBuildingCost}, used by demolition, by undoing a placement, and (per D-03's
     * own acceptance criterion) never refused: crediting resources back can't fail the way spending
     * them can.
     */
    public void refundBuildingCost(ContentId prototypeId) {
        BuildingCost cost = buildingFactory.prototype(prototypeId).cost();
        inventory.add(cost.item(), cost.amount());
    }

    /** Convenience for the closed vanilla set — resolves {@code type}'s own prototype id and delegates to {@link #refundBuildingCost(ContentId)}. */
    public void refundBuildingCost(BuildingType type) {
        refundBuildingCost(VanillaBuildings.idFor(type));
    }

    /**
     * Credit an arbitrary amount of one item to the player's inventory — the general form of
     * {@link #refundBuildingCost}, not tied to any one {@link BuildingType}'s cost. Used when
     * demolishing a {@link com.rustorio.domain.building.Chest} returns its contents ({@code
     * RemoveAction}) and when hand-collecting a chest's contents outright ({@code
     * GrabChestAction}) — a live bug report: a chest's contents and the player's own buildable
     * stock used to be two completely disconnected pools.
     */
    public void creditItem(ItemType item, int amount) {
        inventory.add(item, amount);
    }

    /**
     * Atomically spend several items at once — either every one requested is available and gets
     * deducted, or nothing changes. The multi-item form of {@link #trySpendBuildingCost}'s own
     * atomicity, used by {@code RemoveAction}/{@code GrabChestAction}'s undo to claw back exactly
     * what a demolition or a hand-collection credited — the same "can't afford to undo, stays
     * applied" compromise {@link #trySpendBuildingCost} already makes for a single item.
     */
    public boolean trySpendItems(Map<ItemType, Integer> items) {
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
    public Optional<ItemType> tryManualMine(int x, int y) {
        if (!inBounds(x, y) || !isFree(x, y)) {
            return Optional.empty();
        }
        Coord coord = new Coord(x, y);
        if (tickCount < manualMineReadyAtTick.getOrDefault(coord, 0L)) {
            return Optional.empty();
        }
        Optional<ItemType> ore = buildingFactory.oreLayout().extract(x, y);
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
     * can't afford it, already has it, or hasn't unlocked its prerequisites yet. Only a genuine
     * unlock notifies {@link #researchCompleteListeners} — a refused attempt is not an event.
     */
    public boolean tryUnlockTech(ContentId tech) {
        boolean unlocked = research.unlock(tech);
        if (unlocked) {
            for (ResearchCompleteListener listener : researchCompleteListeners) {
                listener.onResearchComplete(tech);
            }
        }
        return unlocked;
    }

    /** Subscribe a listener to "a technology was actually unlocked" — see {@link #tryUnlockTech}. */
    public void addResearchCompleteListener(ResearchCompleteListener listener) {
        researchCompleteListeners.add(listener);
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
