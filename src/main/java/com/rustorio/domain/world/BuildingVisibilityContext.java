package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.model.TechType;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.VisibilityContext;
import com.rustorio.domain.building.BuildingPrototype;
import java.util.Set;

/**
 * World-backed {@link VisibilityContext} — built from live stats, research and placement history.
 */
public record BuildingVisibilityContext(ProductionStatsView stats, ResearchView research,
        Set<ContentId> placedPrototypes, Registry<ItemType> items, Registry<BuildingPrototype> buildings,
        Registry<TechType> techs) implements VisibilityContext {

    public static BuildingVisibilityContext of(ProductionStatsView stats, ResearchView research,
            Set<ContentId> placedPrototypes, Registry<ItemType> items, Registry<BuildingPrototype> buildings,
            Registry<TechType> techs) {
        return new BuildingVisibilityContext(stats, research, placedPrototypes, items, buildings, techs);
    }

    @Override
    public boolean hasProduced(ContentId itemId) {
        ItemType item = items.get(itemId);
        return stats.total(item) > 0;
    }

    @Override
    public boolean hasPlaced(ContentId buildingId) {
        return placedPrototypes.contains(buildingId);
    }

    @Override
    public boolean isUnlocked(ContentId techId) {
        return research.isUnlocked(techId);
    }

    @Override
    public boolean hasEffect(ContentId effectId) {
        return research.hasEffect(effectId);
    }

    @Override
    public String itemLabel(ContentId itemId) {
        return items.peek(itemId).map(ItemType::label).orElse(itemId.toString());
    }

    @Override
    public String buildingLabel(ContentId buildingId) {
        return buildings.peek(buildingId).map(BuildingPrototype::label).orElse(buildingId.toString());
    }

    @Override
    public String techLabel(ContentId techId) {
        return techs.peek(techId).map(TechType::label).orElse(techId.toString());
    }

    @Override
    public String effectLabel(ContentId effectId) {
        return effectId.path().replace('_', ' ');
    }
}
