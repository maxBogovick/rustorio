/**
 * Mod-facing building contracts. Each type here is a <em>facade</em>: it extends the canonical
 * type in {@code com.rustorio.domain.building} and adds no new members. A mod may
 * {@code implement com.rustorio.api.building.Building} and the engine still sees a
 * {@code domain.building.Building} — same identity, stable import path for docs and new mods.
 *
 * <p><b>Do not facade {@code TickContext}.</b> {@link com.rustorio.domain.building.Building#tick}
 * and {@code accept} take the <em>domain</em> {@code TickContext}. A facade subtype as a method
 * parameter would compile as an overload, not an override — a silent no-op building. Always use
 * {@code com.rustorio.domain.building.TickContext} in those signatures.
 *
 * <p>Records and concrete classes ({@code BuildingPrototype}, {@code SimpleCrafter},
 * {@code ServiceKey}, …) still live under {@code domain.building} until the physical move in a
 * later phase; see {@link com.rustorio.api.content.model} for the same story on content models.
 *
 * <p>Depends on {@code com.rustorio.domain.building} (by design of a facade). Not a place for
 * simulation internals ({@code World}, networks).
 */
@NullMarked
package com.rustorio.api.building;

import org.jspecify.annotations.NullMarked;
