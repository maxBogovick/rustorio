package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.EventBus;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.RecipeKind;
import com.rustorio.domain.TechType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.WorldServices;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What {@link ModLoader#loadAll} produces: the frozen, merged item/fluid/building/kind/map registries, the
 * merged recipe book, the event bus every loaded mod already subscribed to, and the merged prototype
 * renames — everything a game bootstrap needs to build a {@code BuildingFactory}/{@code World} and a
 * save repository from mod-loaded content instead of {@code VanillaItems.frozen()}/{@code
 * VanillaBuildings.frozen()}/{@code RecipeBook.standard()}.
 *
 * <p>{@code serviceProviders} are the capabilities mods registered ({@code
 * RegistrationContext.registerService}) — kept as PROVIDERS, so {@link #newServices()} hands every
 * world its own instances. They were instances once, shared by every world built from one load:
 * closing one game shut the next game's thread pool down before its first tick, and a new game
 * inherited the previous one's per-cell state.
 *
 * <p>{@code prototypeRenames} travels here rather than being re-derived at the save layer because
 * only the mods themselves declare it (see {@code ModDescriptor}), and the save layer has no
 * mod.json to read. {@code com.rustorio.game.GameBootstrap} is what actually hands it to {@code
 * JsonSaveRepository}: a {@code LoadedGame} that carried the renames but never reached the save
 * repository was exactly the defect this component exists to make impossible to repeat.
 */
public record LoadedGame(
        Registry<ItemType> items, Registry<FluidType> fluids,
        Registry<BuildingPrototype> buildings, Registry<RecipeKind> kinds,
        Registry<AuthoredMap> maps, Registry<TechType> techs, RecipeBook recipes, EventBus events,
        Map<ContentId, ContentId> prototypeRenames, List<SkippedMod> skippedMods,
        WorldServices.Builder serviceProviders) {

    public LoadedGame {
        prototypeRenames = Collections.unmodifiableMap(new LinkedHashMap<>(prototypeRenames));
        skippedMods = List.copyOf(skippedMods);
    }

    /**
     * A FRESH set of service instances, one per registered provider — call once per {@code World}.
     * Two worlds built from the same loaded game get independent services, which is what stops one
     * world's shutdown from breaking another and one game's leftovers from reaching the next.
     */
    public WorldServices newServices() {
        return serviceProviders.build();
    }
}
