package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.TechEffect;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.model.TechType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VisibilityReferenceCheck;
import com.rustorio.domain.building.VisibilityRule;

final class VisibilityRuleValidator implements VisibilityReferenceCheck {

    private final Registry<ItemType> items;
    private final Registry<BuildingPrototype> buildings;
    private final Registry<TechType> techs;
    private final Registry<TechEffect> techEffects;

    VisibilityRuleValidator(Registry<ItemType> items, Registry<BuildingPrototype> buildings,
            Registry<TechType> techs, Registry<TechEffect> techEffects) {
        this.items = items;
        this.buildings = buildings;
        this.techs = techs;
        this.techEffects = techEffects;
    }

    static void validate(BuildingPrototype prototype, Registry<ItemType> items, Registry<BuildingPrototype> buildings,
            Registry<TechType> techs, Registry<TechEffect> techEffects) {
        prototype.visibleWhen().ifPresent(rule -> {
            VisibilityRuleValidator validator = new VisibilityRuleValidator(items, buildings, techs, techEffects);
            rule.validateReferences(validator, prototype.id());
        });
    }

    @Override
    public void requireItem(ContentId itemId, ContentId owner) {
        if (items.getOrUnknown(itemId).isEmpty()) {
            throw new ModLoadException("mod '" + ownerOf(owner) + "': building '" + owner
                    + "' visibleWhen produced '" + itemId + "' is not registered");
        }
    }

    @Override
    public void requireBuilding(ContentId buildingId, ContentId owner) {
        if (buildings.getOrUnknown(buildingId).isEmpty()) {
            throw new ModLoadException("mod '" + ownerOf(owner) + "': building '" + owner
                    + "' visibleWhen placed '" + buildingId + "' is not registered");
        }
    }

    @Override
    public void requireTech(ContentId techId, ContentId owner) {
        if (techs.getOrUnknown(techId).isEmpty()) {
            throw new ModLoadException("mod '" + ownerOf(owner) + "': building '" + owner
                    + "' visibleWhen unlocked '" + techId + "' is not registered");
        }
    }

    @Override
    public void requireEffect(ContentId effectId, ContentId owner) {
        if (techEffects.getOrUnknown(effectId).isEmpty()) {
            throw new ModLoadException("mod '" + ownerOf(owner) + "': building '" + owner
                    + "' visibleWhen effect '" + effectId + "' is not registered");
        }
    }

    private static ModId ownerOf(ContentId id) {
        return new ModId(id.namespace());
    }
}
