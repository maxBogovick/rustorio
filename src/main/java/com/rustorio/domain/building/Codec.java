package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemType;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * How a building archetype's captured state turns into (and back from) a plain, JSON-shaped value
 * — the data half of what persistence needs to write {@code {"proto": id, "state": {...}}} to a
 * save file without the domain layer ever importing Jackson (see {@code build.gradle}: the domain
 * doesn't know about JSON).
 *
 * <p>{@code Object} here means "ordinary JDK types a generic JSON writer already knows how to
 * serialize without custom code": {@code Map<String, Object>}, {@code List<Object>}, {@code
 * String}, boxed numbers, {@code Boolean}, or {@code null} — never a domain type like {@link
 * com.rustorio.domain.Direction} or {@link com.rustorio.api.content.model.ItemType} directly, and never a
 * Jackson-specific node type. Converting a domain type to/from one of those plain shapes (a {@code
 * Direction} to its {@code name()}, an {@code ItemType} to its {@code ContentId} string) is this
 * codec's own job, not something a generic (de)serializer infers by reflection — that's the whole
 * point: the persistence layer stays completely generic (it serializes whatever {@code Map}/{@code
 * List} it's handed, the same way for every archetype), and each archetype's own knowledge of its
 * fields lives in exactly one place, next to the state it describes.
 *
 * @param <S> the archetype's own state type (e.g. a {@code MinerState} record) — erased to
 *            {@code Codec<?>} wherever it's stored generically (see {@code BuildingPrototype}),
 *            the same pattern already used for {@link BehaviorFactory}/{@link RestoreFactory}.
 */
public interface Codec<S> {

    /** Convert {@code state} into a plain JSON-shaped value — see the class javadoc for what "plain" means. */
    Object encode(S state);

    /**
     * Convert a plain JSON-shaped value back into {@code S} — {@code data} is exactly what a
     * previous {@link #encode} call produced (modulo migrations, a later card's job), so this
     * method may assume it has the shape this codec itself wrote, not validate arbitrary input.
     *
     * <p>{@code items} is the SAME {@link Registry} the surrounding {@code BuildingFactory} was
     * built with — every real archetype's state names at least one {@link ItemType} somewhere
     * (held cargo, a chest's contents, a filter's chosen item), and different factories can be
     * built with different registries (a modded item registered under a test's own {@code
     * Registry}, not {@link com.rustorio.api.content.vanilla.VanillaItems#frozen()}) — a codec that resolved
     * against a hardcoded vanilla-only registry would fail to decode a save containing modded
     * items, the exact bug class {@code Filter#cycleFilterItem}'s own fix (code review finding S2)
     * already exists to prevent one layer up.
     */
    S decode(Object data, Registry<ItemType> items);

    /**
     * Every real archetype's {@code encode} needs this exact conversion at least once (held
     * cargo, a chest's contents, a filter's chosen item) — centralized here so it's written once,
     * not reinvented slightly differently by each of nine archetypes.
     */
    static @Nullable String encodeItem(@Nullable ItemType item) {
        return item == null ? null : item.id().toString();
    }

    /** The decoding counterpart to {@link #encodeItem} — resolves against {@code items}, same reasoning as {@link #decode}'s own javadoc for why that has to be a parameter, not a hardcoded registry. */
    static @Nullable ItemType decodeItem(@Nullable Object raw, Registry<ItemType> items) {
        return raw == null ? null : items.get(ContentId.of((String) raw));
    }

    /**
     * Reads a field {@code key} out of a decoded {@code Map} that a well-formed {@code decode}
     * implementation may assume is present — {@code Map.get} itself is nullable (a key can
     * genuinely be absent), but every field an archetype's own {@link #encode} always writes
     * unconditionally is NOT one of those cases, so every {@code decode} implementation would
     * otherwise repeat the same null-check-or-throw boilerplate at every single field. Throws
     * (not returns {@code null}) if {@code key} is truly missing — that only happens on a
     * genuinely corrupted save, not a normal one this codec itself produced.
     */
    static <T> T requireField(Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalStateException("missing required field while decoding state: " + key);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }
}
