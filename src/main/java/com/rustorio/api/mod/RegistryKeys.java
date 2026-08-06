package com.rustorio.api.mod;

import com.rustorio.api.registry.RegistryKey;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.TechType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.PlacementRule;
import java.util.List;

/**
 * The registries the base game ships. A mod reaches one through {@link
 * RegistrationContext#registry(RegistryKey)} with a key from here — or declares a key of its own,
 * which works identically: nothing in the lookup treats these as special.
 *
 * <p>{@link #VANILLA} is the declaration ORDER, and order is load-bearing twice over: it is the
 * order registries are frozen in and the order they appear in the load summary, both of which have
 * to read the same on every run. A {@code List}, not a {@code Set.of} — that one randomizes its
 * iteration order per JVM run, which is exactly the trap this repository has hit before.
 */
public final class RegistryKeys {

    public static final RegistryKey<ItemType> ITEMS = new RegistryKey<>("items");

    /**
     * Fluids, separate from {@link #ITEMS} on purpose: a fluid is a volume and an item is a count,
     * and keeping them apart is what stops a pipe and a belt from ever having to ask which of the
     * two they are carrying (see {@link FluidType}).
     */
    public static final RegistryKey<FluidType> FLUIDS = new RegistryKey<>("fluids");

    public static final RegistryKey<BuildingPrototype> BUILDINGS = new RegistryKey<>("buildings");

    /** Every technology — see {@link TechType} for what a mod's technology can and cannot do yet. */
    public static final RegistryKey<TechType> TECHS = new RegistryKey<>("techs");

    /** Every registered recipe pool — see {@link RecipeKind}'s own javadoc. */
    public static final RegistryKey<RecipeKind> KINDS = new RegistryKey<>("kinds");

    /** Every mod-authored map — see {@link AuthoredMap}'s own javadoc. */
    public static final RegistryKey<AuthoredMap> MAPS = new RegistryKey<>("maps");

    /** Every recipe, keyed by its own id — which is what makes a balance mod possible; see {@link RegistrationContext}. */
    public static final RegistryKey<Recipe> RECIPES = new RegistryKey<>("recipes");

    /**
     * Where a building may stand. Registered content rather than a fixed list inside the JSON
     * loader, so a code mod can contribute a genuinely new CONDITION — "next to lava", "on a
     * cliff" — and have its own buildings, and anyone else's, name it from JSON. The vanilla rules
     * register themselves under {@code rustorio:} ids; see {@link PlacementRule}.
     */
    public static final RegistryKey<PlacementRule> PLACEMENT_RULES = new RegistryKey<>("placement rules");

    /** Every key above, in the order registries are frozen and reported — see the class javadoc on why the order is fixed. */
    public static final List<RegistryKey<?>> VANILLA =
            List.of(ITEMS, FLUIDS, PLACEMENT_RULES, BUILDINGS, TECHS, KINDS, MAPS, RECIPES);

    private RegistryKeys() {
    }
}
