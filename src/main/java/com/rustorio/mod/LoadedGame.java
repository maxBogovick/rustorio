package com.rustorio.mod;

import com.rustorio.api.mod.EventBus;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.building.BuildingPrototype;

/**
 * What {@link ModLoader#loadAll} produces: the frozen, merged item/building registries, the merged
 * recipe book, and the event bus every loaded mod already subscribed to — everything a game
 * bootstrap needs to build a {@code BuildingFactory}/{@code World} from mod-loaded content instead
 * of {@code VanillaItems.frozen()}/{@code VanillaBuildings.frozen()}/{@code RecipeBook.standard()}.
 */
public record LoadedGame(Registry<ItemType> items, Registry<BuildingPrototype> buildings, RecipeBook recipes, EventBus events) {
}
