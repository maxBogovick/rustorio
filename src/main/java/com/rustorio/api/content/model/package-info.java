/**
 * Content <em>models</em> a mod registers or resolves. Catalog types that live here now: {@link
 * com.rustorio.api.content.model.FluidType}, {@link com.rustorio.api.content.model.ItemShape},
 * {@link com.rustorio.api.content.model.ItemType}, {@link
 * com.rustorio.api.content.model.RecipeKind}, {@link com.rustorio.api.content.model.TechType},
 * {@link com.rustorio.api.content.model.OrePatch}, {@link
 * com.rustorio.api.content.model.TerrainPatch}, {@link
 * com.rustorio.api.content.model.AuthoredMap}, {@link com.rustorio.api.content.model.Recipe}.
 *
 * <p>Vanilla id/catalog holders live in {@link com.rustorio.api.content.vanilla} ({@link
 * com.rustorio.api.content.vanilla.VanillaItems}, {@link
 * com.rustorio.api.content.vanilla.VanillaSprites}, {@link
 * com.rustorio.api.content.vanilla.VanillaTechs}, {@link
 * com.rustorio.api.content.vanilla.VanillaTechEffects}, {@link
 * com.rustorio.api.content.vanilla.VanillaFluids}).
 *
 * <p>Depends on {@link com.rustorio.api.content.ContentId} only — not on {@code domain}, building,
 * world, persistence, or graphics.
 */
@NullMarked
package com.rustorio.api.content.model;

import org.jspecify.annotations.NullMarked;
