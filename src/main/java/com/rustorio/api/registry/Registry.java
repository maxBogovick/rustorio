package com.rustorio.api.registry;

import com.rustorio.api.content.ContentId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * A two-stage content container: a mutable data stage ({@link #register}/{@link #update})
 * followed by a one-way {@link #freeze()} into a read-only stage ({@link #get}/{@link #rawId}/
 * {@link #iterate}). Two mod authors can each {@code register} their own {@link ContentId}
 * without coordinating; the numeric {@code rawId} is assigned once, at freeze, in an order that
 * depends only on the registered names.
 *
 * <p>Not a singleton — each game/test instance owns its own {@code Registry}, same reason {@code
 * RecipeBook} is constructor-injected: tests need isolated registries.
 *
 * <p>Reads throw before {@link #freeze()}: {@code rawId} isn't assigned yet, so there's nothing
 * correct to return.
 *
 * <p>{@code update} exists because a prototype is an immutable record — mod B has no field to
 * mutate on a prototype mod A registered. It replaces the stored value with {@code
 * updater.apply(current)} and appends the id to {@link #updateLog()}. That log says WHAT was
 * overwritten, not by whom (this method's signature carries no caller identity) — attribution is
 * left to whatever calls {@code update}, not this class.
 *
 * <p>Storage: {@link LinkedHashMap} pre-freeze, a plain {@link List} (index = rawId) post-freeze
 * for the hot lookup — Java generics make a real {@code T[]} either impossible or an
 * unchecked-cast wart, and {@link List#copyOf} is array-backed anyway, so {@link #get(int)} stays
 * O(1) without the cast.
 *
 * @param <T> the content type — opaque to the registry, which is the point.
 */
public final class Registry<T> {

    private final Map<ContentId, T> registered = new LinkedHashMap<>();
    private final List<ContentId> updateLog = new ArrayList<>();

    private boolean frozen = false;
    private List<T> valuesByRawId = List.of();
    private Map<ContentId, Integer> rawIdByContentId = Map.of();

    /** Registers new content under {@code id}. Only legal before {@link #freeze()}. */
    public void register(ContentId id, T value) {
        requireNotFrozen("register");
        if (registered.containsKey(id)) {
            throw new IllegalStateException("content already registered: " + id);
        }
        registered.put(id, value);
    }

    /**
     * Replaces the content already registered under {@code id} with {@code updater.apply(current)}
     * — see the class javadoc for why this exists instead of mutating in place. Only legal before
     * {@link #freeze()}; {@code id} must already be registered.
     */
    public void update(ContentId id, UnaryOperator<T> updater) {
        requireNotFrozen("update");
        T current = registered.get(id);
        if (current == null) {
            throw new NoSuchElementException("cannot update content that was never registered: " + id);
        }
        registered.put(id, updater.apply(current));
        updateLog.add(id);
    }

    /**
     * Un-registers {@code id}, so content another mod added is gone from the game rather than
     * merely overwritten. Only legal before {@link #freeze()}; {@code id} must already be
     * registered, for the same reason {@link #update} insists — a remove that silently does
     * nothing is a typo the author never finds out about.
     *
     * <p>Records the removal in {@link #updateLog()} alongside overwrites: from the point of view
     * of "who changed content I registered", losing it entirely and having it rewritten are the
     * same question. Removing content OTHER content still points at (a recipe's ingredient, a
     * building's cost) is not detected here — {@code ModLoader}'s own validation pass, which runs
     * after every mod has had its say, is where a dangling reference surfaces.
     */
    public void remove(ContentId id) {
        requireNotFrozen("remove");
        if (registered.remove(id) == null) {
            throw new NoSuchElementException("cannot remove content that was never registered: " + id);
        }
        updateLog.add(id);
    }

    /**
     * Closes the data stage: assigns {@code rawId} {@code 0..size()-1} in sorted-{@link ContentId}
     * order (independent of registration/mod-load order) and switches every read method on from
     * throwing to working. A second call is refused, not silently ignored — it would mean
     * something registered after the first {@code freeze()} is being silently dropped.
     */
    public void freeze() {
        if (frozen) {
            throw new IllegalStateException("Registry is already frozen");
        }
        List<ContentId> sortedIds = registered.keySet().stream().sorted().toList();
        List<T> sortedValues = new ArrayList<>(sortedIds.size());
        Map<ContentId, Integer> rawIds = new HashMap<>();
        for (int rawId = 0; rawId < sortedIds.size(); rawId++) {
            ContentId id = sortedIds.get(rawId);
            sortedValues.add(registered.get(id));
            rawIds.put(id, rawId);
        }
        valuesByRawId = List.copyOf(sortedValues);
        rawIdByContentId = Map.copyOf(rawIds); // point lookups only, never iterated - bucket order is moot
        frozen = true;
    }

    /** The content registered under {@code id}. Only legal after {@link #freeze()}; throws if {@code id} was never registered — see {@link #getOrUnknown} for the save-loading-safe variant. */
    public T get(ContentId id) {
        requireFrozen("get");
        T value = registered.get(id);
        if (value == null) {
            throw new NoSuchElementException("no content registered under: " + id);
        }
        return value;
    }

    /** The content at {@code rawId} (as assigned by {@link #freeze()}) — the hot-path lookup, a plain list index, no hashing. */
    public T get(int rawId) {
        requireFrozen("get");
        if (rawId < 0 || rawId >= valuesByRawId.size()) {
            throw new NoSuchElementException(
                    "no such rawId " + rawId + " (registry holds " + valuesByRawId.size() + " entries)");
        }
        return valuesByRawId.get(rawId);
    }

    /** The {@code rawId} {@link #freeze()} assigned to {@code id}. Only legal after {@link #freeze()}. */
    public int rawId(ContentId id) {
        requireFrozen("rawId");
        Integer rawId = rawIdByContentId.get(id);
        if (rawId == null) {
            throw new NoSuchElementException("no content registered under: " + id);
        }
        return rawId;
    }

    /**
     * {@code get(id)}, but {@link Optional#empty()} instead of throwing when {@code id} isn't
     * registered — the save-loading path (a save can reference a mod's content id after that mod
     * was removed) needs to detect this and report it, not crash.
     */
    public Optional<T> getOrUnknown(ContentId id) {
        requireFrozen("getOrUnknown");
        return Optional.ofNullable(registered.get(id));
    }

    /**
     * {@code getOrUnknown(id)}, but legal at ANY point, including before {@link #freeze()} — unlike
     * every other read method here, which needs the frozen {@code rawId} index and therefore
     * refuses to answer early. Needed by the mod loader: resolving a JSON recipe's item reference,
     * or checking whether an earlier-loaded mod already
     * registered something, has to work WHILE registration is still open — {@code
     * registerContent}/{@code modifyContent} run before anything freezes at all. Reads straight off
     * {@link #registered}, which exists unconditionally, so there's nothing frozen-only about
     * answering "is this id registered, and to what" specifically (only {@code rawId} itself is
     * meaningless before freeze, and this method never returns one).
     */
    public Optional<T> peek(ContentId id) {
        return Optional.ofNullable(registered.get(id));
    }

    /** How many entries this registry holds. Only legal after {@link #freeze()} (the count isn't stable before then). */
    public int size() {
        requireFrozen("size");
        return valuesByRawId.size();
    }

    /** Every registered value, in {@code rawId} order (which is sorted-{@link ContentId} order). Only legal after {@link #freeze()}. */
    public List<T> iterate() {
        requireFrozen("iterate");
        return valuesByRawId;
    }

    /**
     * Every {@link ContentId} an {@link #update} call has overwritten, in call order — see the
     * class javadoc for what this can and can't attribute. Available before or after {@link
     * #freeze()}: it's an append-only journal, not a {@code rawId}-ordered view, so premature
     * reads can't leak an unassigned {@code rawId}.
     */
    public List<ContentId> updateLog() {
        return List.copyOf(updateLog);
    }

    private void requireNotFrozen(String operation) {
        if (frozen) {
            throw new IllegalStateException("cannot " + operation + "() after freeze()");
        }
    }

    private void requireFrozen(String operation) {
        if (!frozen) {
            throw new IllegalStateException("cannot " + operation + "() before freeze()");
        }
    }
}
