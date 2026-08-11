/**
 * The mod-facing contract: {@link com.rustorio.api.mod.RustorioMod} (the entry point a mod's own
 * code implements), {@link com.rustorio.api.mod.RegistrationContext} (what a mod registers content
 * through), {@link com.rustorio.api.mod.EventBus} and the narrow event records a mod can subscribe
 * to, plus {@link com.rustorio.api.mod.TechEffect} / {@link com.rustorio.api.dsl.ContentDsl} for the
 * API v2 registration ladder.
 *
 * <p>Wider than a typical "api" package today: catalog content types ({@code ItemType},
 * {@code Recipe}, {@code TechType}, …) already live under {@code com.rustorio.api.content.model}
 * / {@code api.content.vanilla}, but this package still depends on
 * {@code com.rustorio.domain.building} for registration signatures that name
 * {@code BuildingPrototype}/{@code PlacementRule}/{@code ServiceKey}. Remaining simulation types
 * mods may still see ({@code Direction}, {@code Cell}, {@code BuildingStatus}, {@code Appearance},
 * {@code Research}/{@code ResearchView}, {@code BuildingType}, …) stay under {@code domain} until
 * further narrowing — no standalone, implementation-free {@code rustorio-api} Gradle artifact
 * exists yet. Narrowing this back down to a real API surface, once that artifact exists, is a
 * later, deliberately separate concern, not a silent scope change here. Phase 3 already cut
 * {@code domain.world} and {@code domain.action} out of the published jar and the mod classloader;
 * further cuts wait on redesigning signatures that still name kitchen types. Day-to-day entry map
 * for mods and agents lives under {@code docs/} as {@code start-here.md} (stable index, not a
 * design scratchpad).
 */
@NullMarked
package com.rustorio.api.mod;

import org.jspecify.annotations.NullMarked;
