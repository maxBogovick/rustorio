package com.rustorio.domain.building;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.RecipeBook;

/**
 * What a {@link BehaviorFactory}/{@link RestoreFactory} may ask of the world while constructing a
 * building — ore map, recipe book, registries. The live {@link BuildingFactory} implements this;
 * mods see only this port in {@code rustorio-api}, not the factory's vanilla conveniences or
 * network wiring.
 */
public interface BuildingServices {

    OreLayout oreLayout();

    RecipeBook recipeBook();

    Registry<ItemType> items();

    Registry<FluidType> fluids();

    Registry<BuildingPrototype> buildings();
}
