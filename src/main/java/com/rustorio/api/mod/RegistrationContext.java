package com.rustorio.api.mod;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.TechType;
import com.rustorio.domain.building.BuildingPrototype;

/**
 * What a {@link RustorioMod} registers content through, during any of its three lifecycle rounds —
 * the same instance is handed to every mod's {@code registerContent}/{@code modifyContent}/{@code
 * finalFixes} in load order, so a later mod's {@code modifyContent} can see and adjust what an
 * earlier mod already registered — mutual moddability without a content-level dependency graph:
 * the dependency graph only orders mod LOADING, not who can see what inside a round.
 *
 * <p>Every accessor here is the mutable, pre-{@code freeze()} registry itself (not a copy) — a mod
 * calls {@code register}/{@code update}/{@code remove} on it directly, same as {@code
 * VanillaItems}/{@code VanillaBuildings} already do outside the mod system.
 *
 * <p>{@link #recipes()} is a registry like the rest, which is what makes a balance mod possible at
 * all: a recipe is addressed by its own {@link com.rustorio.api.content.ContentId}, so "make
 * vanilla smelting twice as slow" is {@code recipes().update(id, ...)} rather than a copy of the
 * definition someone else owns. It used to be an append-only list, and an appended recipe could
 * never be adjusted or taken back out.
 */
public interface RegistrationContext {

    Registry<ItemType> items();

    /**
     * Every fluid in the game — a mod registers its own here. Separate from {@link #items()} on
     * purpose: a fluid is a volume and an item is a count, and keeping them apart is what stops a
     * pipe and a belt from ever having to ask which of the two they are carrying (see {@link
     * com.rustorio.domain.FluidType}).
     */
    Registry<FluidType> fluids();

    Registry<BuildingPrototype> buildings();

    /** Every technology in the game — a mod registers its own here; see {@link com.rustorio.domain.TechType} for what a mod's technology can and cannot do yet. */
    Registry<TechType> techs();

    /** Every registered recipe pool ("kind") — see {@link RecipeKind}'s own javadoc for what this is and why it exists as a real registered thing now. */
    Registry<RecipeKind> kinds();

    /** Every mod-authored map — see {@link AuthoredMap}'s own javadoc. Nothing in the base game reads this yet (the shipped {@code GameScreen} still picks its {@code OreLayout} before mods load); a mod's own bootstrap, or a later engine feature that lets a player pick a map, is what resolves one from here. */
    Registry<AuthoredMap> maps();

    /** Every recipe in the game, keyed by its own id — register, adjust or remove one here; see the class javadoc. */
    Registry<Recipe> recipes();
}
