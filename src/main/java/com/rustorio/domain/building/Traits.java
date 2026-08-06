package com.rustorio.domain.building;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The optional properties one {@link BuildingPrototype} carries, addressed by {@link TraitKey} —
 * see that class for why a prototype holds a bag of these rather than a growing list of components.
 *
 * <p>Immutable and built once at registration; a prototype is read constantly and never edited, so
 * there is no adding to a bag after the fact. {@link #NONE} is the ordinary case — most buildings
 * are a belt or a chest and declare nothing at all — and it is a shared empty instance, so the
 * ordinary case costs no allocation.
 *
 * <p><b>{@link LinkedHashMap}, not {@code Map.of}.</b> The parity test that proves the vanilla game
 * can be expressed as data compares these bags and prints them when they differ, and the JSON
 * loader fills one in file order; a map whose iteration order is randomized per JVM run would make
 * that comparison's failure message shuffle between runs, which is exactly the trap this repository
 * keeps a rule about. Lookups are by key and never depend on order — the order is for humans.
 */
public final class Traits {

    /** A building that declares no optional property at all — nearly every one. */
    public static final Traits NONE = new Traits(Map.of());

    private final Map<TraitKey<?>, Object> values;

    private Traits(Map<TraitKey<?>, Object> values) {
        this.values = values;
    }

    /**
     * A bag holding {@code values} — copied, so the builder's map cannot be changed underneath a
     * prototype afterwards. Entries whose value is {@code null} are dropped rather than stored:
     * "declared, as nothing" and "not declared" would otherwise be two ways to say the same thing,
     * and callers would have to check both.
     */
    public static Traits of(Map<TraitKey<?>, Object> values) {
        Map<TraitKey<?>, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<TraitKey<?>, Object> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy.isEmpty() ? NONE : new Traits(Collections.unmodifiableMap(copy));
    }

    /** A bag holding exactly one trait — the common case when building a prototype by hand. */
    public static <T> Traits one(TraitKey<T> key, T value) {
        return of(Map.of(key, value));
    }

    /**
     * What this prototype declared under {@code key}, or empty if it declared nothing. The cast is
     * safe by construction: {@link #of} is the only writer, and a key's own {@link TraitKey#type()}
     * is what the loader checks a parsed value against before it ever gets here.
     */
    public <T> Optional<T> get(TraitKey<T> key) {
        return Optional.ofNullable(key.type().cast(values.get(key)));
    }

    /** Every declared trait, in the order it was declared — for a parity dump or a debug view, not for lookups. */
    public Map<TraitKey<?>, Object> asMap() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Traits traits && values.equals(traits.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
