/**
 * The mod-facing contract: {@link com.rustorio.api.mod.RustorioMod} (the entry point a mod's own
 * code implements), {@link com.rustorio.api.mod.RegistrationContext} (what a mod registers content
 * through), {@link com.rustorio.api.mod.EventBus} and the narrow event records a mod can subscribe
 * to, plus {@link com.rustorio.api.mod.TechEffect} / {@link com.rustorio.api.dsl.ContentDsl} for the
 * API v2 registration ladder.
 *
 * <p>Wider than a typical "api" package today: it depends on {@code com.rustorio.domain} and
 * {@code com.rustorio.domain.building} (for {@code ItemType}/{@code BuildingPrototype}/{@code
 * Recipe}/{@code TechType}), not just {@code com.rustorio.api.content}/{@code api.registry}. Those
 * content types physically live in the domain today because no standalone {@code rustorio-api}
 * Gradle artifact exists yet — a mod cannot register an item or a building prototype without
 * naming its type, so this package has to see it. Narrowing this back down to a real,
 * implementation-free API surface, once a standalone artifact exists, is a later, deliberately
 * separate concern, not a silent scope change here. Phase 3 already cut {@code domain.world} and
 * {@code domain.action} out of the published jar and the mod classloader; further cuts wait on
 * redesigning signatures that still name kitchen types. Day-to-day entry map for mods and agents
 * lives under {@code docs/} as {@code start-here.md} (stable index, not a design scratchpad).
 */
@NullMarked
package com.rustorio.api.mod;

import org.jspecify.annotations.NullMarked;
