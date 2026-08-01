package com.rustorio.api.mod;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.building.BuildingPrototype;
import java.util.List;

/**
 * What a {@link RustorioMod} registers content through, during any of its three lifecycle rounds —
 * the same instance is handed to every mod's {@code registerContent}/{@code modifyContent}/{@code
 * finalFixes} in load order, so a later mod's {@code modifyContent} can see and adjust what an
 * earlier mod already registered — mutual moddability without a content-level dependency graph:
 * the dependency graph only orders mod LOADING, not who can see what inside a round.
 *
 * <p>{@code items()}/{@code buildings()} are the mutable, pre-{@code freeze()} registries
 * themselves (not a copy) — a mod calls {@code register}/{@code update} on them directly, same as
 * {@code VanillaItems}/{@code VanillaBuildings} already do outside the mod system. {@code Recipe}
 * has no {@link com.rustorio.api.content.ContentId} of its own (see its own javadoc), so it can't
 * live in a {@code Registry} — {@link #addRecipe} accumulates a plain list instead, in call order,
 * which is why load order has to be deterministic (see {@code ModLoader}) for the resulting {@code
 * RecipeBook} to come out the same regardless of which mods happen to be installed.
 */
public interface RegistrationContext {

    Registry<ItemType> items();

    Registry<BuildingPrototype> buildings();

    /** Every registered recipe pool ("kind") — see {@link RecipeKind}'s own javadoc for what this is and why it exists as a real registered thing now. */
    Registry<RecipeKind> kinds();

    /** Adds a recipe to the game's recipe book — see the class javadoc for why this isn't a {@code Registry}. */
    void addRecipe(Recipe recipe);

    /** Every recipe added so far, in the order {@link #addRecipe} was called — read-only view for a later round to inspect. */
    List<Recipe> recipes();
}
