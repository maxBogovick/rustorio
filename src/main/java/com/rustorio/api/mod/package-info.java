/**
 * The mod-facing contract: {@link com.rustorio.api.mod.RustorioMod} (the entry point a mod's own
 * code implements), {@link com.rustorio.api.mod.RegistrationContext} (what a mod registers content
 * through), {@link com.rustorio.api.mod.EventBus} and the narrow event records a mod can subscribe
 * to.
 *
 * <p>Wider than a typical "api" package today: it depends on {@code com.rustorio.domain} and
 * {@code com.rustorio.domain.building} (for {@code ItemType}/{@code BuildingPrototype}/{@code
 * Recipe}/{@code TechType}), not just {@code com.rustorio.api.content}/{@code api.registry}. Those
 * content types physically live in the domain today because no standalone {@code rustorio-api}
 * Gradle artifact exists yet — a mod cannot register an item or a building prototype without
 * naming its type, so this package has to see it. Narrowing this back down to a real,
 * implementation-free API surface, once a standalone artifact exists, is a later, deliberately
 * separate concern, not a silent scope change here.
 */
@NullMarked
package com.rustorio.api.mod;

import org.jspecify.annotations.NullMarked;
