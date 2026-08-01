package com.rustorio.domain;

import com.rustorio.api.content.ContentId;

/**
 * A named, independently-declared recipe pool — the registered counterpart to what {@link Recipe}
 * points at with {@link Recipe#type()} and {@code BuildingPrototype#recipeKind()} both already
 * carry as a bare {@link ContentId}. Before this existed, a "kind" had no independent existence at
 * all: it only "existed" as whatever a building's own id happened to default to, which meant the
 * content editor had nothing real to offer in a dropdown and no way to catch a typo — see {@code
 * RecipeKindJsonLoader} for how these load, and {@code com.rustorio.mod.ModLoader#validateContent}
 * for how every recipe/building's own kind reference is checked against the full set of real ones
 * (vanilla {@link BuildingType} pools, every registered {@code RecipeKind}, and every building's
 * own id) once loading finishes.
 *
 * <p>Deliberately minimal — just an identity and a display name, nothing else: a kind carries no
 * behavior of its own (that's still entirely the building archetype's job), so there's nothing
 * else to declare on it.
 */
public record RecipeKind(ContentId id, String label) {
}
