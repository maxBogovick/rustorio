package com.rustorio.domain.world;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Research;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

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
public final class World {

    /**
     * A cell's position, and the {@link TreeMap} key {@link #buildings} is ordered by — {@link
     * #compareTo} sorts by {@code x} first, then {@code y}, exactly matching the packed-{@code
     * long} ordering this replaced ({@code (x << 32) | y}), without the bit arithmetic: a record
     * is precisely the tool Java added for "small compound value used as a key."
     */
    private record Coord(int x, int y) implements Comparable<Coord> {
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

    /** Whether ore lies under the cell — a miner can only be placed on it. */
    public boolean hasOre(int x, int y) {
        return buildingFactory.oreLayout().hasOre(x, y);
    }

    /** Whether the cell holds no building at all. */
    public boolean isFree(int x, int y) {
        return !buildings.containsKey(new Coord(x, y));
    }

    public boolean placeMiner(int x, int y) {
        if (inBounds(x, y) && hasOre(x, y) && isFree(x, y)) {
            buildings.put(new Coord(x, y), buildingFactory.create(BuildingType.MINER, Direction.RIGHT));
            return true;
        }
        return false;
    }

    public boolean placeChest(int x, int y) {
        return placeIfFree(BuildingType.CHEST, x, y, Direction.RIGHT);
    }

    public boolean placeFurnace(int x, int y, Direction direction) {
        return placeIfFree(BuildingType.FURNACE, x, y, direction);
    }

    public boolean placePress(int x, int y, Direction direction) {
        return placeIfFree(BuildingType.PRESS, x, y, direction);
    }

    /**
     * Place a belt facing {@code direction}. Unlike every other {@code place*}, this doesn't
     * reduce to {@link #placeIfFree}: the new tile also has to join the {@link Belt} segment its
     * same-direction neighbors (if any) belong to — see {@link Belt#attachToNeighbors}.
     */
    public boolean placeBelt(int x, int y, Direction direction) {
        if (!inBounds(x, y) || !isFree(x, y)) {
            return false;
        }
        Belt belt = (Belt) buildingFactory.create(BuildingType.BELT, direction);
        buildings.put(new Coord(x, y), belt);
        attachToSegment(belt, x, y, direction);
        return true;
    }

    private void attachToSegment(Belt belt, int x, int y, Direction direction) {
        Belt behind = beltNeighbor(x - direction.dx(), y - direction.dy(), direction).orElse(null);
        Belt ahead = beltNeighbor(x + direction.dx(), y + direction.dy(), direction).orElse(null);
        belt.attachToNeighbors(behind, ahead);
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

    public boolean placeUndergroundIn(int x, int y, Direction direction) {
        return placeIfFree(BuildingType.UNDERGROUND_IN, x, y, direction);
    }

    public boolean placeUndergroundOut(int x, int y, Direction direction) {
        return placeIfFree(BuildingType.UNDERGROUND_OUT, x, y, direction);
    }

    public boolean placeSplitter(int x, int y, Direction direction) {
        return placeIfFree(BuildingType.SPLITTER, x, y, direction);
    }

    public boolean placeLab(int x, int y) {
        return placeIfFree(BuildingType.LAB, x, y, Direction.RIGHT);
    }

    private boolean placeIfFree(BuildingType type, int x, int y, Direction direction) {
        if (inBounds(x, y) && isFree(x, y)) {
            buildings.put(new Coord(x, y), buildingFactory.create(type, direction));
            return true;
        }
        return false;
    }

    /**
     * Place a building of the chosen kind, facing {@code direction} — one door for every
     * building's construction. Each kind's own placement rule (a miner needs ore) lives behind
     * it; direction matters to belts, furnaces/presses, tunnels and splitters, and is ignored by
     * miners, chests and labs.
     */
    public boolean place(BuildingType type, int x, int y, Direction direction) {
        return switch (type) {
            case MINER -> placeMiner(x, y);
            case CHEST -> placeChest(x, y);
            case FURNACE -> placeFurnace(x, y, direction);
            case BELT -> placeBelt(x, y, direction);
            case SPLITTER -> placeSplitter(x, y, direction);
            case PRESS -> placePress(x, y, direction);
            case UNDERGROUND_IN -> placeUndergroundIn(x, y, direction);
            case UNDERGROUND_OUT -> placeUndergroundOut(x, y, direction);
            case LAB -> placeLab(x, y);
        };
    }

    /** Same, with the default direction — convenient for buildings that ignore it anyway. */
    public boolean place(BuildingType type, int x, int y) {
        return place(type, x, y, Direction.RIGHT);
    }

    /**
     * Demolish the building on a cell and return it, if there was one. Returns the SAME object
     * that stood there (not a fresh equivalent) — {@code UndoAction} needs to be able to put back
     * exactly what was demolished, buffers and all, not a blank replacement.
     */
    public Optional<Building> removeBuilding(int x, int y) {
        Building removed = buildings.remove(new Coord(x, y));
        if (removed != null && Building.unwrap(removed) instanceof Belt belt) {
            belt.leaveSegment();
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Place an ALREADY-BUILT building into a cell, with none of {@link #place}'s checks. Used by
     * the persistence layer and by undo/redo: the placement rules were already satisfied when the
     * building was first built (or first loaded); re-checking a past decision serves no purpose.
     */
    public void restoreBuilding(int x, int y, Building building) {
        buildings.put(new Coord(x, y), building);
        if (Building.unwrap(building) instanceof Belt belt) {
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
     * One world step: every building lives its tick, knowing where it stands.
     *
     * <p>Everything that prefers the descending pass ({@link Building#prefersDescendingTick()}
     * true — buildings without a facing, and rightward/downward belts) goes first, high
     * coordinates to low; the rest (leftward/upward belts) go afterward, low to high. Each
     * building says which traversal it needs; the world never asks whether something is a belt.
     */
    public void tick() {
        for (Map.Entry<Coord, Building> entry : buildings.descendingMap().entrySet()) {
            if (entry.getValue().prefersDescendingTick()) {
                tickEntry(entry);
            }
        }
        for (Map.Entry<Coord, Building> entry : buildings.entrySet()) {
            if (!entry.getValue().prefersDescendingTick()) {
                tickEntry(entry);
            }
        }
    }

    private void tickEntry(Map.Entry<Coord, Building> entry) {
        Coord coord = entry.getKey();
        entry.getValue().tick(this, coord.x(), coord.y());
    }

    /** Tell every {@link ProductionListener} an item was produced — once per finished batch. */
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
    public boolean offerForward(int x, int y, Item item) {
        return offer(x, y, item);
    }

    /**
     * Look at a cell without offering it anything or consuming anything — unlike {@link #offer}.
     * Needed by tunnel entrances searching for their exit partner ahead.
     */
    public Optional<Building> peek(int x, int y) {
        return Optional.ofNullable(buildings.get(new Coord(x, y)));
    }

    /** Read-only view — see {@link ProductionStatsView} for why this isn't {@code ProductionStats} itself. */
    public ProductionStatsView stats() {
        return stats;
    }

    /** Read-only view — see {@link ResearchView} for why this isn't {@code Research} itself. */
    public ResearchView research() {
        return research;
    }

    /** Add research points (called once per finished {@code Lab} batch) — the one door for mutating research. */
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

    /** Visit every building with its coordinates — used by rendering and by persistence. */
    public void forEachBuilding(BuildingVisitor visitor) {
        for (Map.Entry<Coord, Building> entry : buildings.entrySet()) {
            Coord coord = entry.getKey();
            visitor.visit(coord.x(), coord.y(), entry.getValue());
        }
    }

    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
    }
}
