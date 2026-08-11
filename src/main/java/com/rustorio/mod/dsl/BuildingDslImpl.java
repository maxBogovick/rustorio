package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.dsl.BuildingDsl;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.BuildingType;
import com.rustorio.api.content.model.FluidType;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.BehaviorFactory;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.RestoreFactory;
import com.rustorio.domain.building.SimpleCrafter;
import com.rustorio.domain.building.SimpleCrafterSpec;
import com.rustorio.domain.building.TraitKey;
import com.rustorio.domain.building.Traits;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.VanillaCategories;
import com.rustorio.domain.building.VanillaTraits;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class BuildingDslImpl implements BuildingDsl {

    private final RegistrationContext context;
    private final ContentId id;

    private @Nullable String label;
    private @Nullable ContentId costItemId;
    private int costAmount = 1;
    private @Nullable ContentId placementId;
    private @Nullable ContentId texture;
    private int footprintWidth = 1;
    private int footprintHeight = 1;
    private @Nullable ContentId category;
    private int powerDemand;
    private int powerOutput;
    private int poleRadius;
    private @Nullable ContentId fluidInputId;
    private @Nullable ContentId fluidOutputId;
    private @Nullable ContentId speedTech;
    private @Nullable BuildingType archetype;
    private @Nullable BehaviorFactory behavior;
    private @Nullable RestoreFactory restore;
    private @Nullable Codec<?> codec;
    private @Nullable SimpleCrafterSpec simpleCrafterSpec;

    BuildingDslImpl(RegistrationContext context, ContentId id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public BuildingDsl label(String label) {
        this.label = label;
        return this;
    }

    @Override
    public BuildingDsl cost(String itemPathOrId, int amount) {
        this.costItemId = DslIds.resolve(context, itemPathOrId);
        this.costAmount = amount;
        return this;
    }

    @Override
    public BuildingDsl placement(String rulePathOrId) {
        this.placementId = DslIds.resolvePlacement(context, rulePathOrId);
        return this;
    }

    @Override
    public BuildingDsl texture(ContentId sprite) {
        this.texture = sprite;
        return this;
    }

    @Override
    public BuildingDsl size(int width, int height) {
        this.footprintWidth = width;
        this.footprintHeight = height;
        return this;
    }

    @Override
    public BuildingDsl category(ContentId category) {
        this.category = category;
        return this;
    }

    @Override
    public BuildingDsl powerDemand(int demand) {
        this.powerDemand = demand;
        return this;
    }

    @Override
    public BuildingDsl powerOutput(int output) {
        this.powerOutput = output;
        return this;
    }

    @Override
    public BuildingDsl poleRadius(int radius) {
        this.poleRadius = radius;
        return this;
    }

    @Override
    public BuildingDsl fluidInput(String fluidPathOrId) {
        this.fluidInputId = DslIds.resolve(context, fluidPathOrId);
        return this;
    }

    @Override
    public BuildingDsl fluidOutput(String fluidPathOrId) {
        this.fluidOutputId = DslIds.resolve(context, fluidPathOrId);
        return this;
    }

    @Override
    public BuildingDsl speedTech(ContentId techId) {
        this.speedTech = techId;
        return this;
    }

    @Override
    public BuildingDsl archetype(BuildingType type) {
        this.archetype = type;
        return this;
    }

    @Override
    public BuildingDsl behavior(BehaviorFactory create) {
        this.behavior = create;
        return this;
    }

    @Override
    public BuildingDsl restore(RestoreFactory restore) {
        this.restore = restore;
        return this;
    }

    @Override
    public BuildingDsl codec(Codec<?> codec) {
        this.codec = codec;
        return this;
    }

    @Override
    public BuildingDsl simpleCrafter(SimpleCrafterSpec spec) {
        this.simpleCrafterSpec = spec;
        return this;
    }

    @Override
    public BuildingDsl simpleCrafter(String inputPathOrId, String outputPathOrId, int workTicks) {
        this.simpleCrafterSpec = SimpleCrafterSpec.of(
                DslIds.resolve(context, inputPathOrId),
                DslIds.resolve(context, outputPathOrId),
                workTicks);
        return this;
    }

    @Override
    public ContentId register() {
        if (costItemId == null) {
            throw new IllegalStateException("building '" + id + "' needs cost()");
        }
        if (placementId == null) {
            throw new IllegalStateException("building '" + id + "' needs placement()");
        }
        if (texture == null) {
            throw new IllegalStateException("building '" + id + "' needs texture()");
        }

        ItemType costItem = context.requireItem(costItemId);
        PlacementRule placement = context.placementRules().peek(placementId).orElseThrow(
                () -> new IllegalStateException("building '" + id + "' names unknown placement '"
                        + placementId + "'"));

        BehaviorFactory create;
        RestoreFactory restoreBehavior;
        Codec<?> stateCodec;
        boolean acceptsSpeedEffects = false;
        int bufferMax = 0;
        int speedMultiplier = 1;
        @Nullable ContentId inheritedSpeedTech = null;
        ContentId recipeKind = id;
        @Nullable ItemType fuelItem = null;

        if (simpleCrafterSpec != null) {
            if (archetype != null || behavior != null) {
                throw new IllegalStateException("building '" + id
                        + "': simpleCrafter() cannot combine with archetype()/behavior()");
            }
            SimpleCrafterSpec spec = simpleCrafterSpec;
            // Resolve at register-time (same contract as cost()): a typo must fail the mod load,
            // not the first place/tick that constructs the building.
            context.requireItem(spec.input());
            context.requireItem(spec.output());
            create = (self, direction, factory) -> SimpleCrafter.create(self, direction, spec, factory.items());
            restoreBehavior = (self, decoded, factory) ->
                    SimpleCrafter.restore(self, (SimpleCrafter.State) decoded, spec, factory.items());
            stateCodec = SimpleCrafter.CODEC;
            bufferMax = spec.inputMax();
            acceptsSpeedEffects = false;
        } else if (archetype != null) {
            BuildingPrototype borrowed = VanillaBuildings.frozen().get(VanillaBuildings.idFor(archetype));
            create = borrowed.behavior();
            restoreBehavior = borrowed.restoreBehavior();
            stateCodec = borrowed.codec();
            bufferMax = borrowed.bufferMax();
            speedMultiplier = borrowed.speedMultiplier();
            acceptsSpeedEffects = borrowed.acceptsSpeedEffects();
            inheritedSpeedTech = borrowed.speedTech();
            // Keep the borrowed pool and fuel — otherwise a DSL FURNACE gets a private empty pool
            // and stops needing coal, silently weaker than the JSON path.
            recipeKind = borrowed.recipeKind();
            fuelItem = borrowed.fuelItem();
            if (hasPowerFields() && !VanillaBuildings.honorsPower(archetype)) {
                throw new IllegalStateException("building '" + id + "': power* has no effect on archetype '"
                        + archetype + "' — only " + VanillaBuildings.powerAwareArchetypes() + " honor power");
            }
        } else if (behavior != null && restore != null && codec != null) {
            create = behavior;
            restoreBehavior = restore;
            stateCodec = codec;
        } else {
            throw new IllegalStateException("building '" + id
                    + "' needs archetype(), simpleCrafter(...), or behavior()+restore()+codec()");
        }

        Map<TraitKey<?>, Object> traits = new LinkedHashMap<>();
        if (fluidInputId != null) {
            traits.put(VanillaTraits.FLUID_INPUT, requireFluid(fluidInputId));
        }
        if (fluidOutputId != null) {
            traits.put(VanillaTraits.FLUID_OUTPUT, requireFluid(fluidOutputId));
        }
        PowerSpec power = buildPowerSpec();
        if (power != null) {
            traits.put(VanillaTraits.POWER, power);
        }
        ContentId resolvedSpeedTech = speedTech != null ? speedTech : inheritedSpeedTech;
        if (resolvedSpeedTech != null) {
            traits.put(VanillaTraits.SPEED_TECH, resolvedSpeedTech);
        }
        if (category != null) {
            traits.put(VanillaCategories.CATEGORY, category);
        }

        String resolvedLabel = label != null ? label : id.path();
        context.buildings().register(id, new BuildingPrototype(
                id, resolvedLabel, new BuildingCost(costItem, costAmount), placement, texture,
                footprintWidth, footprintHeight, bufferMax, speedMultiplier, acceptsSpeedEffects,
                create, restoreBehavior, stateCodec, recipeKind, fuelItem, Traits.of(traits)));
        return id;
    }

    private boolean hasPowerFields() {
        return powerDemand > 0 || powerOutput > 0 || poleRadius > 0;
    }

    private @Nullable PowerSpec buildPowerSpec() {
        if (!hasPowerFields()) {
            return null;
        }
        return new PowerSpec(poleRadius, powerOutput, powerDemand);
    }

    private FluidType requireFluid(ContentId fluidId) {
        return context.fluids().peek(fluidId).orElseThrow(() -> new IllegalStateException(
                "building '" + id + "' refers to unknown fluid '" + fluidId + "'"));
    }
}
