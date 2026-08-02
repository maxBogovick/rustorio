package com.rustorio.mod;

import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.building.BuildingPrototype;
import java.util.ArrayList;
import java.util.List;

/**
 * The one {@link RegistrationContext} instance shared by every mod across all three lifecycle
 * rounds (see {@link ModLoader}) — a later-loaded mod's round sees exactly what an earlier one
 * already registered, because both write into these same two {@link Registry} instances and this
 * same recipe list.
 */
final class GameRegistrationContext implements RegistrationContext {

    private final Registry<ItemType> items = new Registry<>();
    private final Registry<BuildingPrototype> buildings = new Registry<>();
    private final Registry<RecipeKind> kinds = new Registry<>();
    private final Registry<AuthoredMap> maps = new Registry<>();
    private final List<Recipe> recipes = new ArrayList<>();

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
    public void addRecipe(Recipe recipe) {
        recipes.add(recipe);
    }

    @Override
    public List<Recipe> recipes() {
        return List.copyOf(recipes);
    }
}
