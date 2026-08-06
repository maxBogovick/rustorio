package com.rustorio.api.mod;

import com.rustorio.api.registry.Registry;
import com.rustorio.api.registry.RegistryKey;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.TechType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.PlacementRule;

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

    /**
     * The registry under {@code key} — the one method an implementation actually has to provide;
     * every named accessor below is this call with a constant from {@link RegistryKeys}.
     *
     * <p>A mod may pass a key of its own to hold a content kind the base game has never heard of.
     * Nothing here treats the vanilla keys as special: they are constants in a class, not cases in
     * a list, which is the whole point — a new kind of content used to mean editing this interface,
     * its implementation, the loaded-game record, the loader's freeze list and its log line before
     * it did anything at all.
     *
     * @throws java.util.NoSuchElementException if nothing is registered under {@code key} — reaching
     *     for a registry that does not exist is a programming error, not a content one, so it is
     *     loud rather than an empty registry that silently swallows everything put into it
     */
    <T> Registry<T> registry(RegistryKey<T> key);

    default Registry<ItemType> items() {
        return registry(RegistryKeys.ITEMS);
    }

    /**
     * Every fluid in the game — a mod registers its own here. Separate from {@link #items()} on
     * purpose: a fluid is a volume and an item is a count, and keeping them apart is what stops a
     * pipe and a belt from ever having to ask which of the two they are carrying (see {@link
     * com.rustorio.domain.FluidType}).
     */
    default Registry<FluidType> fluids() {
        return registry(RegistryKeys.FLUIDS);
    }

    default Registry<BuildingPrototype> buildings() {
        return registry(RegistryKeys.BUILDINGS);
    }

    /** Where a building may stand, as registered content — see {@link RegistryKeys#PLACEMENT_RULES} for why this is a registry and not a fixed list in the JSON loader. */
    default Registry<PlacementRule> placementRules() {
        return registry(RegistryKeys.PLACEMENT_RULES);
    }

    /** Every technology in the game — a mod registers its own here; see {@link com.rustorio.domain.TechType} for what a mod's technology can and cannot do yet. */
    default Registry<TechType> techs() {
        return registry(RegistryKeys.TECHS);
    }

    /** Every registered recipe pool ("kind") — see {@link RecipeKind}'s own javadoc for what this is and why it exists as a real registered thing now. */
    default Registry<RecipeKind> kinds() {
        return registry(RegistryKeys.KINDS);
    }

    /** Every mod-authored map — see {@link AuthoredMap}'s own javadoc. Nothing in the base game reads this yet (the shipped {@code GameScreen} still picks its {@code OreLayout} before mods load); a mod's own bootstrap, or a later engine feature that lets a player pick a map, is what resolves one from here. */
    default Registry<AuthoredMap> maps() {
        return registry(RegistryKeys.MAPS);
    }

    /** Every recipe in the game, keyed by its own id — register, adjust or remove one here; see the class javadoc. */
    default Registry<Recipe> recipes() {
        return registry(RegistryKeys.RECIPES);
    }
}
