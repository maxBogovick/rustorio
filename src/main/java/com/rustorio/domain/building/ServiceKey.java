package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;

/**
 * Names one capability a world may or may not have — an outbound HTTP client, a clock, anything a
 * building needs that is neither content nor a cell of the map — and carries that capability's
 * type, so reading one back needs no cast. The exact shape {@link TraitKey} already uses for the
 * same job one level down (an optional property of a PROTOTYPE rather than of the whole world).
 *
 * <p>Why this exists: {@link TickContext} is the only door a building has onto its world, and
 * before this key every new capability had to become new methods ON that interface. One archetype
 * that fetched a URL added three at once ({@code requestFetch}/{@code pollFetch}/{@code
 * lastResponseBody}), which put a single mod's vocabulary into the engine's narrowest public
 * contract and left the next such archetype no choice but to do it again. {@link
 * TickContext#service} is the one method those three collapsed into, and it never needs a fourth:
 * a mod declares its own key, registers its own implementation, and the engine stays unaware that
 * the capability exists at all.
 *
 * <p>Identity is {@link #id} alone (a record's generated equality over both components amounts to
 * the same thing, since a given id always declares the same type). Two mods naming the same id are
 * asking for the same capability — which is the point: a mod can consume a service another mod
 * provides without either of them knowing the other's Java types, exactly as {@link TraitKey}
 * already lets one mod read another's trait.
 *
 * @param <T> what a world stores under this key
 */
public record ServiceKey<T>(ContentId id, Class<T> type) {
}
