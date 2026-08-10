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
import com.rustorio.domain.building.ServiceKey;
import java.util.function.Supplier;

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
     * <p>Nothing here treats the vanilla keys as special: they are constants in a class, not cases
     * in a list, which is the whole point — a new kind of content used to mean editing this
     * interface, its implementation, the loaded-game record, the loader's freeze list and its log
     * line before it did anything at all. That openness is real on the ENGINE side and is what
     * makes adding a content kind a one-line change there.
     *
     * <p><b>A mod cannot yet contribute a key of its own</b>, and this javadoc used to say it
     * could. Looking one up is all this method does; the only call that CREATES a registry under a
     * new key is package-private in {@code com.rustorio.mod} — a package {@code ModClassLoader}
     * refuses to delegate and {@code apiJar} does not ship, so a mod cannot reach it. Passing an
     * unknown key therefore throws (see below) rather than lazily creating one. Opening this up is
     * a public-API decision for the owner, not something to widen in passing; until then a mod's
     * own collection lives in a field of its {@link RustorioMod} and simply forgoes {@code rawId},
     * freezing and the load summary.
     *
     * @throws java.util.NoSuchElementException if nothing is registered under {@code key} — reaching
     *     for a registry that does not exist is a programming error, not a content one, so it is
     *     loud rather than an empty registry that silently swallows everything put into it
     */
    <T> Registry<T> registry(RegistryKey<T> key);

    /**
     * Registers a capability this mod's own buildings will reach through {@link
     * com.rustorio.domain.building.TickContext#service} — an HTTP client, a clock, anything that is
     * neither content nor a cell of the map. The world every mod's content ends up in is built with
     * whatever was registered here, so a code mod needs no cooperation from the game's own startup
     * code to make its archetypes work.
     *
     * <p>Separate from {@link #registry}: a service is ONE object provided by whoever implements
     * it, not a collection of addressable content. It gets no {@code rawId}, never reaches a save
     * file, and is not something another mod can enumerate — only ask for by key.
     *
     * <p>A PROVIDER, not a ready-made instance: it is called once per {@code World}, so every game
     * gets its own. Registering an instance instead made one object shared by every world built
     * from the same load — closing one game shut its thread pool down for the next, and a new game
     * inherited the previous one's per-cell state. A mod that genuinely wants one shared instance
     * returns the same object from its provider, which is then an explicit decision.
     *
     * <p>A later mod registering the same {@link ServiceKey} replaces an earlier one's provider,
     * the same way it may {@code update} content another mod registered. That is deliberate:
     * replacing the implementation behind a key is how one mod extends or instruments another's
     * capability.
     *
     * <p>If the service holds a background resource (a thread pool, a connection), implement {@link
     * AutoCloseable} — {@link com.rustorio.domain.building.WorldServices#closeAll} calls it when the
     * WORLD it belongs to goes away, so a mod never needs its own shutdown hook.
     */
    <T> void registerService(ServiceKey<T> key, Supplier<T> provider);

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
