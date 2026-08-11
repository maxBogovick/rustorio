package com.rustorio.domain.building;

import com.rustorio.domain.Cell;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * <p><b>Published mod API caveat.</b> This class (including {@link Reach}) stays in
 * {@code rustorio-api} because {@link PowerNode} names it in public signatures. Prefer
 * {@link TickContext} for ordinary power draws; holding a grid is an L3 concern.
 *
 * <p>One electrical grid: a connected group of poles plus a pool of power that is filled at the start
 * of every tick and drawn down by the machines that ask for it during that tick.
 *
 * <p><b>Only poles are members.</b> A generator or a machine is never IN a network — it is merely
 * covered by a pole. That is what makes a grid re-plannable: moving one pole rewires everything
 * around it, and no machine has to be rebuilt for its wiring to change. It is also why this class
 * is a component of POLES only, unlike {@link FluidNetwork}, whose members are every tile of the
 * plumbing.
 *
 * <p><b>Two poles are connected when one stands inside the other's coverage.</b> Poles reach across
 * a gap, so a grid is not the edge-sharing component {@link FluidNetwork} builds — but it is
 * maintained the same way, incrementally on place and demolish, never rediscovered by scanning.
 *
 * <p><b>The pool is filled before anything ticks.</b> {@code World} asks every generator for its
 * output at the top of the tick and {@link #contribute}s it here; machines {@link #draw} from it
 * during their own ticks. So a machine can never run on power that was not generated first, and the
 * generators' order among themselves is the world's fixed coordinate order.
 *
 * <p><b>Short supply is resolved by tick order, not by proportion.</b> The design sketch called for
 * every machine to get a proportional share; a machine here either runs a whole tick or does not run
 * at all, so there is no half-speed to hand it. Machines therefore draw in the order the world ticks
 * them — which {@code TickScheduler} fixes by coordinate, descending for everything that has no
 * facing to care about — and the ones a short grid cannot reach report {@code NO_POWER} instead of
 * all of them stuttering. Deterministic, and it makes a browned-out factory legible: the same
 * machines go dark every tick rather than a different random third of them, so there is one place to
 * go look rather than a flicker to chase.
 */
public final class PowerNetwork {

    /** This grid's poles, ordered by coordinate — the same determinism argument as {@link FluidNetwork}. */
    private final NavigableMap<Cell, PowerNode> poles = new TreeMap<>();

    /** Power left to hand out this tick. */
    private long available;

    /** What was put in this tick, kept separately from {@link #available} so the UI and tests can read the grid's output without draining it. */
    private long supplied;

    /** The tick the pool was last emptied and refilled on, so a new tick always starts from zero. */
    private long poolTick = -1;

    PowerNetwork() {
    }

    /**
     * Add a generator's output to this tick's pool — see the class javadoc on when {@code World}
     * calls this. Public, unlike the structural methods below, for the same reason {@link
     * FluidPort#insert} is: moving power in and out is what a machine's side of the world does every
     * tick, while building and splitting a grid is this package's own business.
     */
    public void contribute(long amount, long tick) {
        startTick(tick);
        available += amount;
        supplied += amount;
    }

    /**
     * Take {@code amount} out of this tick's pool. All or nothing: a machine that cannot have its
     * whole demand gets none of it and reports {@code NO_POWER} rather than half-running — see the
     * class javadoc on why that beats a proportional share here.
     *
     * @return whether the draw succeeded
     */
    public boolean draw(long amount, long tick) {
        startTick(tick);
        if (amount > available) {
            return false;
        }
        available -= amount;
        return true;
    }

    /** What this grid's generators put in this tick, whether or not anything has drawn it yet. */
    public long supplied(long tick) {
        startTick(tick);
        return supplied;
    }

    /**
     * A stable identity for this grid: the smallest pole cell it holds, by coordinate — the same
     * geometry-not-hash identity {@link FluidNetwork#anchor()} gives, so the overlay can paint every
     * cell one grid covers the same colour across runs and saves. A grid always has at least one
     * pole (it is a component OF poles), so this is never empty.
     */
    public Cell anchor() {
        return poles.firstKey();
    }

    /** How many poles this grid is made of — what a merge or a split is asserted against. */
    int poleCount() {
        return poles.size();
    }

    /**
     * Empty the pool if this is a new tick. Power is not storable: whatever was generated and not
     * drawn last tick is simply gone, which is the whole reason accumulators would be a feature of
     * their own rather than something this pool quietly already does.
     */
    private void startTick(long tick) {
        if (poolTick == tick) {
            return;
        }
        poolTick = tick;
        available = 0;
        supplied = 0;
    }

    /**
     * Put {@code node} at {@code cell} into whichever grid its already-placed neighbors form,
     * merging every one within reach into a single grid — the same narrow-door shape {@link
     * FluidNetwork#attach} uses, and the caller supplies the neighbors for the same reason: {@code
     * World} owns the cell map.
     */
    static void attach(PowerNode node, Cell cell, List<PowerNode> neighbors) {
        PowerNetwork target = null;
        for (PowerNode neighbor : neighbors) {
            PowerNetwork candidate = neighbor.network();
            if (candidate == null) {
                continue; // an unplaced pole has nothing to join — defensive; World never passes one
            }
            if (target == null) {
                target = candidate;
            } else if (candidate != target) {
                target.absorb(candidate);
            }
        }
        PowerNetwork joined = target == null ? new PowerNetwork() : target;
        joined.poles.put(cell, node);
        node.joinNetwork(joined);
    }

    /**
     * Take a demolished pole out, and split what is left if that pole was the only thing holding two
     * groups together.
     *
     * <p>{@code reach} answers "can these two poles see each other" for the remaining poles —
     * supplied by {@code World} for the same reason {@link #attach}'s neighbors are: which pole sees
     * which is a question about positions, and this class holds no map of the world.
     */
    static void detach(PowerNode node, Cell cell, Reach reach) {
        PowerNetwork network = node.network();
        if (network == null) {
            return;
        }
        network.poles.remove(cell);
        node.joinNetwork(null);
        network.splitAfterRemoval(reach);
    }

    /** Whether two poles can see each other — {@code World} answers, since it knows where poles stand. */
    @FunctionalInterface
    public interface Reach {
        boolean connects(Cell one, Cell other);
    }

    private void absorb(PowerNetwork other) {
        for (Map.Entry<Cell, PowerNode> entry : other.poles.entrySet()) {
            poles.put(entry.getKey(), entry.getValue());
            entry.getValue().joinNetwork(this);
        }
        other.poles.clear();
    }

    /**
     * Rebuild the components of whatever poles are left. The component holding the smallest
     * coordinate keeps this network object; every other moves into one of its own.
     *
     * <p>Nothing has to be done about generators or machines: neither is a member, and both find
     * their grid through the pole covering them, which now resolves to whichever network that pole
     * ended up in.
     */
    private void splitAfterRemoval(Reach reach) {
        List<List<Cell>> components = components(reach);
        if (components.size() <= 1) {
            return;
        }
        for (int i = 1; i < components.size(); i++) {
            PowerNetwork branch = new PowerNetwork();
            for (Cell cell : components.get(i)) {
                PowerNode moved = poles.remove(cell);
                if (moved != null) {
                    branch.poles.put(cell, moved);
                    moved.joinNetwork(branch);
                }
            }
        }
    }

    /**
     * Connected components of {@link #poles}, walked in coordinate order so the first one out is
     * always the one holding the smallest cell — the same determinism argument as {@link
     * FluidNetwork#components}. O(poles²) within a single grid, on a player action only: poles are
     * counted in dozens, not in the thousands that pipes are.
     */
    private List<List<Cell>> components(Reach reach) {
        Set<Cell> seen = new HashSet<>(); // looked up, never iterated
        List<List<Cell>> found = new ArrayList<>();
        for (Cell start : poles.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            List<Cell> component = new ArrayList<>();
            Deque<Cell> queue = new ArrayDeque<>();
            queue.addLast(start);
            while (!queue.isEmpty()) {
                Cell cell = queue.removeFirst();
                component.add(cell);
                for (Cell other : poles.keySet()) {
                    if (!seen.contains(other) && reach.connects(cell, other) && seen.add(other)) {
                        queue.addLast(other);
                    }
                }
            }
            found.add(component);
        }
        return found;
    }
}
