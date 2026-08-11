/**
 * Pure game domain: items, directions, technologies, recipes and world generation rules.
 *
 * <p>Nothing here depends on {@code com.rustorio.domain.building}, {@code
 * com.rustorio.domain.world}, {@code com.rustorio.persistence} or {@code com.graphics} — this is
 * the innermost ring of the architecture (ports-and-adapters / hexagonal style): value types and
 * small strategies that everything else is built from. The ring <em>may</em> depend on
 * {@code com.rustorio.api.content}, {@code com.rustorio.api.content.model},
 * {@code com.rustorio.api.content.vanilla}, and {@code com.rustorio.api.registry} value types
 * ({@link com.rustorio.api.content.ContentId},
 * {@link com.rustorio.api.content.model.FluidType}, {@link
 * com.rustorio.api.content.model.ItemType}, {@link com.rustorio.api.content.model.ItemShape},
 * {@link com.rustorio.api.content.model.Recipe}, {@link
 * com.rustorio.api.content.model.RecipeKind}, {@link com.rustorio.api.content.model.TechType},
 * {@link com.rustorio.api.content.model.AuthoredMap}, {@link
 * com.rustorio.api.content.model.OrePatch}, {@link
 * com.rustorio.api.content.model.TerrainPatch}, {@link
 * com.rustorio.api.content.vanilla.VanillaItems}, {@link
 * com.rustorio.api.content.vanilla.VanillaSprites}, {@link
 * com.rustorio.api.content.vanilla.VanillaTechs}, {@link
 * com.rustorio.api.content.vanilla.VanillaTechEffects}, {@link
 * com.rustorio.api.content.vanilla.VanillaFluids}, {@link com.rustorio.api.registry.Registry}, …) —
 * those are the public content/registry contracts, not simulation kitchen.
 */
@NullMarked
package com.rustorio.domain;

import org.jspecify.annotations.NullMarked;
