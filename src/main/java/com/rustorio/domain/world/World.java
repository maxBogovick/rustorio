package com.rustorio.domain.world;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Research;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.SpeedModule;
import com.rustorio.domain.building.TickContext;
import java.util.ArrayList;
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
     * <p>Package-private, not {@code private}: {@link TickScheduler} needs the type to accept
     * {@link #buildings} without copying it (P3-03, BUG_FIX_PROGRESS.md).
     */
    record Coord(int x, int y) implements Comparable<Coord> {
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

    /** Total items ever produced — survives individual buildings being demolished. */
    private final ProductionStats stats = new ProductionStats();

    /** Research points and unlocked technologies — also survives individual demolitions. */
    private final Research research = new Research();

    /**
     * Who wants to hear about every produced item. Statistics is always subscribed, but the list
     * isn't wired to it by name — another listener can sit alongside it without touching this
     * class at all.
     */
    private final List<ProductionListener> productionListeners = new ArrayList<>();

    public World(int width, int height) {
        this(width, height, BuildingFactory.standard());
    }

    public World(int width, int height, BuildingFactory buildingFactory) {
        this.width = width;
        this.height = height;
        this.buildingFactory = buildingFactory;
        productionListeners.add(stats);
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

    /** Whether the cell holds no building at all. */
    public boolean isFree(int x, int y) {
        return !buildings.containsKey(new Coord(x, y));
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
        if (!inBounds(x, y) || !isFree(x, y) || !buildingFactory.canPlace(type, x, y)) {
            return false;
        }
        Building building = buildingFactory.create(type, direction);
        buildings.put(new Coord(x, y), building);
        if (building instanceof Belt belt) {
            attachToSegment(belt, x, y, direction);
        }
        return true;
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
        Building removed = buildings.remove(new Coord(x, y));
        if (removed != null && Building.unwrap(removed) instanceof Belt belt) {
            BuildingFactory.detachBelt(belt);
        }
        return Optional.ofNullable(removed);
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
        buildings.put(new Coord(x, y), building);
        if (Building.unwrap(building) instanceof Belt belt) {
            BuildingFactory.detachBelt(belt);
            attachToSegment(belt, x, y, belt.direction());
        }
    }

    /** Demolish every building and reset statistics/research — the world is ready for a fresh load. */
    public void clear() {
        buildings.clear();
        stats.clear();
        research.clear();
    }

    /**
     * One world step: every building lives its tick, knowing where it stands. The ordering rules
     * (and the P2-07 cross-segment gap they used to leave open) live in {@link TickScheduler} now
     * — see its javadoc — extracted out of {@code World} in P3-03, BUG_FIX_PROGRESS.md.
     */
    public void tick() {
        TickScheduler.tick(buildings, this);
    }

    /** Tell every {@link ProductionListener} an item was produced — once per finished batch. */
    @Override
    public void notifyProduced(Item item) {
        for (ProductionListener listener : productionListeners) {
            listener.onProduced(item);
        }
    }

    /**
     * Try to hand an item to any neighbor (up/down/left/right) — without notifying listeners.
     * {@link com.rustorio.domain.building.Miner} and {@link com.rustorio.domain.building.Furnace}
     * both hold a finished item and retry delivery every tick until it succeeds; "produced" (once,
     * at the moment of completion) and "delivered" (however many retries it takes) are
     * deliberately different events, or statistics would credit output that never happened.
     */
    @Override
    public boolean tryDeliverToNeighbor(int x, int y, Item item) {
        return offer(x + 1, y, item)
                || offer(x - 1, y, item)
                || offer(x, y + 1, item)
                || offer(x, y - 1, item);
    }

    /**
     * Hand an item to whatever building sits at a cell, if it's willing.
     *
     * <p>The world never enumerates building sorts through {@code instanceof} here — it asks
     * {@link Building#accept} and the building answers for itself.
     */
    private boolean offer(int x, int y, Item item) {
        Building building = buildings.get(new Coord(x, y));
        return building != null && building.accept(this, item);
    }

    /**
     * Hand an item to ONE specific neighbor — addressed, not broadcast like {@link
     * #tryDeliverToNeighbor}. A belt moves cargo in exactly one direction, never "wherever fits."
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
        return Optional.ofNullable(buildings.get(new Coord(x, y)));
    }

    /** Read-only view — see {@link ProductionStatsView} for why this isn't {@code ProductionStats} itself. */
    public ProductionStatsView stats() {
        return stats;
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

    /** Overwrite research progress wholesale from a save file — see {@code JsonSaveRepository}. */
    public void restoreResearch(Research.Snapshot snapshot) {
        research.restore(snapshot);
    }

    /** Overwrite production totals wholesale from a save file — see {@code JsonSaveRepository}. */
    public void restoreStats(ProductionStats.Snapshot snapshot) {
        stats.restore(snapshot);
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
     * <p>{@link Coord#compareTo} orders by {@code x} first, so {@link #buildings}{@code .subMap}
     * from {@code (minX, minY)} to {@code (maxX, maxY)} narrows the scan to the right X range, but
     * NOT to the right Y range for any X strictly between the bounds — the total order only
     * constrains Y at the two boundary X values. The extra {@code y} check below is not optional.
     */
    public void forEachBuildingIn(int minX, int minY, int maxX, int maxY, BuildingVisitor visitor) {
        if (minX > maxX || minY > maxY) {
            return; // an empty rectangle (e.g. the camera entirely off the map) — nothing to visit
        }
        var range = buildings.subMap(new Coord(minX, minY), true, new Coord(maxX, maxY), true);
        for (Map.Entry<Coord, Building> entry : range.entrySet()) {
            Coord coord = entry.getKey();
            if (coord.y() >= minY && coord.y() <= maxY) {
                visitor.visit(coord.x(), coord.y(), entry.getValue());
            }
        }
    }

    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
    }
}
