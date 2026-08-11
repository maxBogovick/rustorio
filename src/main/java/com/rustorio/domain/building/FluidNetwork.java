package com.rustorio.domain.building;

import com.rustorio.domain.Cell;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.FluidType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * <p><b>Published mod API caveat.</b> This class stays in {@code rustorio-api} because
 * {@link FluidNode} names it in public signatures. Prefer {@link TickContext} / {@link FluidPort}
 * for ordinary fluid work; holding a network is an L3 concern.
 *
 * <p>A connected run of pipes and tanks as ONE bucket: a single fluid, a single volume, and a capacity
 * that is the sum of its tiles'. The fluid counterpart to {@link BeltSegment}, built the same way —
 * assembled incrementally as the player places and demolishes tiles, never rediscovered by scanning
 * the world — with three deliberate differences.
 *
 * <p><b>Connectivity is 2D, not a straight run.</b> A tile joins across all four sides and has no
 * direction at all, so a network is a general connected component rather than {@link BeltSegment}'s
 * ordered line. That is why this class indexes its tiles by {@link Cell} in a {@link TreeMap}: a
 * component has to be walked (to split it, to hand every tile its share) and the walk must produce
 * the same answer whatever order the tiles were built or restored in. Ordering by coordinate does
 * that; ordering by insertion or by hash would not.
 *
 * <p><b>No fluid MOVES per tick.</b> {@link BeltSegment} has to cascade cargo tile by tile every
 * step; a bucket does not — "how full is this pipe" is {@code amount / capacity} of the whole
 * network, so lengthening a run adds no per-tick arithmetic at all. Only its EDGES do work, and
 * only once something (a pump, a boiler) actually pushes or pulls through a {@link FluidPort}. The
 * expensive operation, splitting on demolition, is O(size) and happens on a player action, the same
 * trade {@code PlacementVeto} and {@link BeltSegment#remove} already make.
 *
 * <p>That is a claim about THIS class, not about what a pipe costs the world: {@code TickScheduler}
 * still visits every placed building twice a tick (once to clear arrival marks, once to tick it).
 * Status bookkeeping reads {@link Building#status()} (and {@link Pipe#status()} is a constant —
 * no {@link com.rustorio.domain.Appearance} allocation on that path). A network of a thousand pipes is therefore cheap,
 * not free — visit cost remains even when the network itself does no per-tick fluid work.
 *
 * <p><b>One fluid per network, by construction.</b> An empty network accepts anything; a network
 * holding water and a network holding steam do not merge, and the tile between them stays a border
 * belonging to whichever side it joined first. There is no mixing rule anywhere in this class
 * because there is no way to express a mixture.
 *
 * <p>Public for the same reason {@link BeltSegment} is: {@link FluidNode#network()} returns this
 * type, so a mod's own node implementation — living outside this package — has to be able to name
 * it. Every method that mutates a network stays package-private; a foreign implementer reaches
 * them through {@code BuildingFactory}'s narrow doors, never directly.
 */
public final class FluidNetwork implements FluidPort {

    /** Cloned once here rather than per flood-fill step: {@code values()} copies the array on every call. */
    private static final Direction[] SIDES = Direction.values();

    /** This network's tiles, ordered by coordinate — see the class javadoc for why that order is the point. */
    private final NavigableMap<Cell, FluidNode> tiles = new TreeMap<>();

    /** {@code null} exactly when {@link #amount} is zero: an empty network has no fluid and will take any. */
    private @Nullable FluidType fluid;

    private long amount;

    /** The sum of {@link FluidNode#fluidCapacity()} over {@link #tiles}, kept in step with it rather than re-summed. */
    private long capacity;

    /**
     * {@link #shareOf}'s answer for every tile, computed once and dropped on any change. Keyed by
     * node IDENTITY, not by {@code equals}: a mod's node class may define its own equality, and two
     * tiles that call themselves equal are still two separate tiles here. Never iterated — only
     * looked up — so the map kind says nothing about determinism; the ORDER the shares are computed
     * in is {@link #tiles}' coordinate order, which is what actually has to be stable.
     */
    private @Nullable Map<FluidNode, Long> shares;

    FluidNetwork() {
    }

    @Override
    public @Nullable FluidType fluid() {
        return fluid;
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    /**
     * A stable identity for this network: the smallest cell it covers, by coordinate. The overlay
     * paints every tile of one network the same colour, and a save-independent, run-independent
     * identity has to come from geometry rather than object hash — the same reason this class keys
     * its tiles by {@link Cell} at all (see the class javadoc). Every attached tile is a member, so
     * a placed pipe's network is never empty here.
     */
    public Cell anchor() {
        return tiles.firstKey();
    }

    @Override
    public long insert(FluidType wanted, long requested) {
        FluidType current = fluid;
        if (requested <= 0 || (current != null && !current.equals(wanted))) {
            return 0;
        }
        long accepted = Math.min(requested, capacity - amount);
        if (accepted <= 0) {
            return 0;
        }
        fluid = wanted;
        amount += accepted;
        invalidateShares();
        return accepted;
    }

    @Override
    public long extract(FluidType wanted, long requested) {
        FluidType current = fluid;
        if (requested <= 0 || current == null || !current.equals(wanted)) {
            return 0;
        }
        long given = Math.min(requested, amount);
        amount -= given;
        if (amount == 0) {
            fluid = null;
        }
        invalidateShares();
        return given;
    }

    /**
     * Put {@code node} at {@code cell} into whichever network its already-placed neighbors form,
     * merging those that can share a fluid and starting a fresh one when there are none.
     *
     * <p>{@code neighbors} is every already-placed fluid tile touching {@code cell}, in a fixed
     * side order — the caller ({@code World}, through {@code BuildingFactory}'s narrow door) owns
     * the cell map and so is the only one that can find them. Which SIDE each came from is
     * deliberately not passed: a network has no direction, so the answer would be unused.
     *
     * <p>A neighbor whose fluid the newcomer cannot share is skipped rather than merged — that is
     * how a water network and a steam network stay two networks with a tile between them.
     *
     * <p><b>Every merge is weighed against what the NEWCOMER carries, not only against the network
     * already chosen.</b> Checking against the chosen one alone looks equivalent and is not: an
     * emptied network accepts anything, so if it is picked first it would happily swallow a steam
     * network, and the water the newcomer is carrying would then have nowhere to go — silently
     * destroyed by an {@link #insert} that returns zero, with a water tank left sitting in a steam
     * network. Both promises this class makes ("one fluid per network by construction", and volume
     * conserved across a demolish and undo) fail together, and the everyday way to reach it is
     * undoing a demolition, not anything exotic.
     *
     * <p>Because the newcomer's own fluid is weighed first, this is also what lets a save be
     * restored from geometry alone: every tile that CARRIES something rejoins the side carrying the
     * same thing, so the volumes come back exactly as they were written. An EMPTY tile sitting on a
     * boundary is the one thing geometry cannot pin down — it carries no evidence of which side it
     * used to belong to — and it joins whichever compatible neighbor comes first in the caller's
     * side order. Deterministic, and free of consequence (it holds nothing either way), but it does
     * mean a border can shift by one empty tile across a save and load.
     */
    static void attach(FluidNode node, Cell cell, List<FluidNode> neighbors) {
        FluidType carried = node.detachedFluid();
        FluidNetwork target = null;
        for (FluidNode neighbor : neighbors) {
            FluidNetwork candidate = neighbor.network();
            if (candidate == null) {
                continue; // an unplaced neighbor has nothing to join — defensive, World never passes one
            }
            if (target == null) {
                if (candidate.accepts(carried)) {
                    target = candidate;
                }
            } else if (candidate != target && target.accepts(candidate.fluid)
                    && compatible(carried, candidate.fluid)) {
                target.absorb(candidate);
            }
        }
        FluidNetwork joined = target == null ? new FluidNetwork() : target;
        joined.add(cell, node);
    }

    /**
     * Take {@code node} at {@code cell} out of its network — it leaves carrying its own share (see
     * {@link FluidNode}), and whatever it was holding the two sides of together may now be two
     * networks, so the remainder is re-split.
     */
    static void detach(FluidNode node, Cell cell) {
        FluidNetwork network = node.network();
        if (network != null) {
            network.remove(cell, node);
        }
    }

    /**
     * This tile's share of the network's volume — what a save writes for it, and what it takes with
     * it when demolished. The whole network's {@link #amount} split across its tiles in proportion
     * to their capacity, so the shares always sum back to exactly {@link #amount}: that identity is
     * what makes saving per tile and reloading into one bucket lossless.
     */
    long shareOf(FluidNode node) {
        Long share = shares().get(node);
        return share == null ? 0 : share;
    }

    /** Whether a network holding {@code other} may be merged into this one — one of the two must be empty, or they must agree. */
    private boolean accepts(@Nullable FluidType other) {
        return compatible(fluid, other);
    }

    /**
     * Whether two fluids may share one network: either agrees with the other, or one of them is
     * absent (an empty network, or a tile carrying nothing). Static and two-argument so that {@link
     * #attach} can ask the question about a fluid belonging to NEITHER network it is looking at —
     * the one the newcomer is carrying — which {@link #accepts} cannot express, since it always
     * compares against its own.
     */
    private static boolean compatible(@Nullable FluidType one, @Nullable FluidType other) {
        return one == null || other == null || one.equals(other);
    }

    private void add(Cell cell, FluidNode node) {
        tiles.put(cell, node);
        capacity += node.fluidCapacity();
        node.joinNetwork(this);
        FluidType carried = node.detachedFluid();
        long carriedAmount = node.detachedAmount();
        node.setDetached(null, 0);
        invalidateShares();
        if (carried != null && carriedAmount > 0) {
            // Fits by construction: capacity has already grown by this tile's own, the tile's share
            // can never have exceeded it, and attach only ever hands a tile to a network its fluid
            // agrees with. Checked rather than assumed because the one way this used to come out
            // false — an emptied network merging a foreign one in first — destroyed the fluid
            // without a trace, and a broken invariant is worth an exception rather than a silent
            // subtraction from the player's factory.
            long accepted = insert(carried, carriedAmount);
            if (accepted != carriedAmount) {
                throw new IllegalStateException("tile at " + cell + " carrying " + carriedAmount + " of "
                        + carried.id() + " joined a network that took only " + accepted
                        + " (network holds " + amount + " of " + (fluid == null ? "nothing" : fluid.id())
                        + ", capacity " + capacity + ")");
            }
        }
    }

    /** Pour {@code other} into this network and empty it — the caller has already checked {@link #accepts}. */
    private void absorb(FluidNetwork other) {
        FluidType incoming = other.fluid;
        if (incoming != null) {
            fluid = incoming;
        }
        amount += other.amount;
        capacity += other.capacity;
        for (Map.Entry<Cell, FluidNode> entry : other.tiles.entrySet()) {
            tiles.put(entry.getKey(), entry.getValue());
            entry.getValue().joinNetwork(this);
        }
        other.tiles.clear();
        other.amount = 0;
        other.capacity = 0;
        other.fluid = null;
        other.invalidateShares();
        invalidateShares();
    }

    private void remove(Cell cell, FluidNode node) {
        long share = shareOf(node);
        if (tiles.remove(cell) == null) {
            return; // not one of ours — defensive; World keeps its cell map and this in step
        }
        FluidType leaving = fluid;
        capacity -= node.fluidCapacity();
        amount -= share;
        if (amount == 0) {
            fluid = null;
        }
        node.joinNetwork(null);
        node.setDetached(share > 0 ? leaving : null, share);
        invalidateShares();
        splitAfterRemoval();
    }

    /**
     * Re-split what is left after a tile was taken out. The component holding the smallest
     * coordinate keeps this network object (so the tiles that stay, stay); every other component
     * moves into a network of its own, taking a share of the volume proportional to its capacity.
     *
     * <p>Proportional to CAPACITY, not to tile count as the design sketch first put it: a tank is
     * one tile but holds many pipes' worth, so splitting by count would hand a component more fluid
     * than it can physically contain the moment tiles differ in size. By capacity, each part is
     * always within its own limit — see {@link #divideByWeight}.
     */
    private void splitAfterRemoval() {
        List<Component> components = components();
        if (components.size() <= 1) {
            return;
        }
        List<Long> capacities = new ArrayList<>(components.size());
        for (Component component : components) {
            capacities.add(component.capacity());
        }
        List<Long> amounts = divideByWeight(amount, capacities, capacity);
        FluidType splitFluid = fluid;
        for (int i = 1; i < components.size(); i++) {
            Component component = components.get(i);
            FluidNetwork branch = new FluidNetwork();
            for (Map.Entry<Cell, FluidNode> member : component.members()) {
                tiles.remove(member.getKey());
                branch.tiles.put(member.getKey(), member.getValue());
                member.getValue().joinNetwork(branch);
            }
            branch.capacity = component.capacity();
            branch.amount = amounts.get(i);
            branch.fluid = branch.amount == 0 ? null : splitFluid;
        }
        capacity = capacities.get(0);
        amount = amounts.get(0);
        if (amount == 0) {
            fluid = null;
        }
        invalidateShares();
    }

    /**
     * The connected components of {@link #tiles}, each as a list of cells. Components come out in
     * the order of their smallest cell — {@link #tiles} is walked in coordinate order and each
     * unvisited cell starts a new one — which is what lets {@link #splitAfterRemoval} name "the
     * first component" without depending on build or restore order.
     */
    private List<Component> components() {
        Set<Cell> seen = new HashSet<>(); // looked up, never iterated — its own order is not observed
        List<Component> found = new ArrayList<>();
        for (Map.Entry<Cell, FluidNode> start : tiles.entrySet()) {
            if (!seen.add(start.getKey())) {
                continue;
            }
            List<Map.Entry<Cell, FluidNode>> members = new ArrayList<>();
            long componentCapacity = 0;
            Deque<Map.Entry<Cell, FluidNode>> queue = new ArrayDeque<>();
            queue.addLast(start);
            while (!queue.isEmpty()) {
                Map.Entry<Cell, FluidNode> member = queue.removeFirst();
                members.add(member);
                componentCapacity += member.getValue().fluidCapacity();
                Cell cell = member.getKey();
                for (Direction side : SIDES) {
                    Cell neighbor = new Cell(cell.x() + side.dx(), cell.y() + side.dy());
                    FluidNode neighborNode = tiles.get(neighbor);
                    if (neighborNode != null && seen.add(neighbor)) {
                        queue.addLast(Map.entry(neighbor, neighborNode));
                    }
                }
            }
            found.add(new Component(members, componentCapacity));
        }
        return found;
    }

    /**
     * One connected piece found by {@link #components}, with its capacity already summed during the
     * walk — the tiles are in hand there, so re-looking each one up afterwards would be a second
     * pass for a number the first pass could just as well carry.
     */
    private record Component(List<Map.Entry<Cell, FluidNode>> members, long capacity) {
    }

    private Map<FluidNode, Long> shares() {
        Map<FluidNode, Long> cached = shares;
        if (cached != null) {
            return cached;
        }
        List<Long> weights = new ArrayList<>(tiles.size());
        for (FluidNode node : tiles.values()) {
            weights.add(node.fluidCapacity());
        }
        List<Long> parts = divideByWeight(amount, weights, capacity);
        Map<FluidNode, Long> computed = new IdentityHashMap<>(tiles.size());
        int index = 0;
        for (FluidNode node : tiles.values()) {
            computed.put(node, parts.get(index));
            index++;
        }
        shares = computed;
        return computed;
    }

    private void invalidateShares() {
        shares = null;
    }

    /**
     * Split {@code total} into one part per weight, proportional to the weights and summing back to
     * exactly {@code total}: each part is the floored proportion, and the leftover units go one
     * each to the earliest parts. Earliest, not "wherever the remainder lands", so the result
     * depends only on the order the caller passes — coordinate order, everywhere it is used.
     *
     * <p>No part can exceed its own weight, which is what makes this safe for both uses (a tile's
     * share, a component's volume): when {@code total < weightSum} the floored proportion is
     * strictly below the weight, leaving room for the +1; when they are equal there is no leftover
     * to hand out at all.
     *
     * <p>{@code total * weight} stays inside a {@code long} for any world this game can hold: both
     * factors are bounded by the total capacity on the map, which is cells times per-tile capacity
     * — some billions, whose product is still an order of magnitude below {@link Long#MAX_VALUE}.
     */
    private static List<Long> divideByWeight(long total, List<Long> weights, long weightSum) {
        List<Long> parts = new ArrayList<>(weights.size());
        long assigned = 0;
        for (long weight : weights) {
            long part = weightSum == 0 ? 0 : total * weight / weightSum;
            parts.add(part);
            assigned += part;
        }
        for (int i = 0; assigned < total && i < parts.size(); i++) {
            parts.set(i, parts.get(i) + 1);
            assigned++;
        }
        return parts;
    }
}
