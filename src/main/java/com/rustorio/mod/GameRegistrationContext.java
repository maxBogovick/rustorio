package com.rustorio.mod;

import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.TechType;
import com.rustorio.domain.building.BuildingPrototype;

/**
 * The one {@link RegistrationContext} instance shared by every mod across all three lifecycle
 * rounds (see {@link ModLoader}) — a later-loaded mod's round sees exactly what an earlier one
 * already registered, because every one of them writes into these same {@link Registry} instances.
 */
final class GameRegistrationContext implements RegistrationContext {

    private final Registry<ItemType> items = new Registry<>();
    private final Registry<BuildingPrototype> buildings = new Registry<>();
    private final Registry<RecipeKind> kinds = new Registry<>();
    private final Registry<AuthoredMap> maps = new Registry<>();
    private final Registry<Recipe> recipes = new Registry<>();
    private final Registry<TechType> techs = new Registry<>();

    @Override
    public Registry<ItemType> items() {
        return items;
    }

    @Override
    public Registry<BuildingPrototype> buildings() {
        return buildings;
    }

    @Override
    public Registry<RecipeKind> kinds() {
        return kinds;
    }

    @Override
    public Registry<AuthoredMap> maps() {
        return maps;
    }

    @Override
    public Registry<Recipe> recipes() {
        return recipes;
    }

    @Override
    public Registry<TechType> techs() {
        return techs;
    }
}
