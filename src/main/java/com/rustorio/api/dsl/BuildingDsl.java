package com.rustorio.api.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.BehaviorFactory;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.RestoreFactory;
import com.rustorio.domain.building.SimpleCrafterSpec;

/**
 * Fluent registration of one {@link com.rustorio.domain.building.BuildingPrototype} — either by
 * borrowing a vanilla archetype (L1) or wiring custom behavior / {@link SimpleCrafterSpec} (L2).
 */
public interface BuildingDsl {

    BuildingDsl label(String label);

    BuildingDsl cost(String itemPathOrId, int amount);

    /**
     * Placement rule id — bare path tries {@code rustorio:} first (shipped rules), then the current
     * mod namespace; a full {@code namespace:path} is taken as-is.
     */
    BuildingDsl placement(String rulePathOrId);

    BuildingDsl texture(ContentId sprite);

    BuildingDsl size(int width, int height);

    BuildingDsl category(ContentId category);

    BuildingDsl powerDemand(int demand);

    BuildingDsl powerOutput(int output);

    BuildingDsl poleRadius(int radius);

    BuildingDsl fluidInput(String fluidPathOrId);

    BuildingDsl fluidOutput(String fluidPathOrId);

    /** Technology that halves this building's work time once unlocked (Miner/Furnace/SimpleCrafter). */
    BuildingDsl speedTech(ContentId techId);

    /** L1: reuse an existing Java archetype with this prototype's data. */
    BuildingDsl archetype(BuildingType type);

    /** L2: fully custom create/restore/codec. */
    BuildingDsl behavior(BehaviorFactory create);

    BuildingDsl restore(RestoreFactory restore);

    BuildingDsl codec(Codec<?> codec);

    /** L2 battery: input → N ticks → output, with a stock codec. Spec item ids must be full {@code ns:path}. */
    BuildingDsl simpleCrafter(SimpleCrafterSpec spec);

    /**
     * L2 battery with bare paths resolved under the current mod namespace (or full ids with
     * {@code ':'}). Prefer this over {@link SimpleCrafterSpec#of(String, String, int)} when paths
     * are local to the mod.
     */
    BuildingDsl simpleCrafter(String inputPathOrId, String outputPathOrId, int workTicks);

    ContentId register();
}
