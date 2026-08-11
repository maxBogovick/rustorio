/**
 * Catalog of content <em>models</em> a mod registers or resolves — {@link
 * com.rustorio.domain.ItemType}, {@link com.rustorio.domain.Recipe}, {@link
 * com.rustorio.domain.FluidType}, {@link com.rustorio.domain.TechType}, {@link
 * com.rustorio.domain.AuthoredMap}, {@link com.rustorio.domain.RecipeKind}, {@link
 * com.rustorio.domain.ItemShape}, {@link com.rustorio.domain.OrePatch}, {@link
 * com.rustorio.domain.TerrainPatch}.
 *
 * <p>Those types still live in {@code com.rustorio.domain} (records cannot be facade-extended).
 * This package exists so docs and {@code rustorio-api} have a stable <em>address</em> for the
 * future physical move; import the domain types today. Vanilla id constants —
 * {@link com.rustorio.api.content.vanilla}.
 *
 * <p>Depends on nothing of its own yet (documentation package). After the move it will own the
 * model types and {@code domain} will import them — dependency arrow flips outward.
 */
@NullMarked
package com.rustorio.api.content.model;

import org.jspecify.annotations.NullMarked;
