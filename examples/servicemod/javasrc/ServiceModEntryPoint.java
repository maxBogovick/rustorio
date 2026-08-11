package com.servicemod.jarmod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.vanilla.VanillaSprites;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.PlacementRule;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This mod's whole footprint on the engine: one prototype and one service, registered through the
 * ordinary {@link RegistrationContext} every mod gets. No class in {@code com.rustorio} or {@code
 * com.graphics} mentions this mod, this package, or this service — which is exactly what the
 * acceptance test around it verifies.
 */
public final class ServiceModEntryPoint implements RustorioMod {

    public static final ContentId GENERATOR_ID = ContentId.of("servicemod:pulse_generator");
    static final ContentId PULSE_ITEM_ID = ContentId.of("servicemod:pulse");

    /**
     * Writes {@link PulseGenerator.PulseState} as plain JDK types and reads it back — the engine's
     * save layer serializes whatever this produces with no Jackson annotation anywhere in this mod,
     * which is the arrangement that keeps JSON out of the domain.
     */
    private static final Codec<PulseGenerator.PulseState> CODEC = new Codec<>() {
        @Override
        public Object encode(PulseGenerator.PulseState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("produced", state.produced());
            return data;
        }

        @Override
        public PulseGenerator.PulseState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new PulseGenerator.PulseState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "produced"));
        }
    };

    @Override
    public void registerContent(RegistrationContext context) {
        // A provider, not an instance: the engine calls it once per World, so two games never share
        // one service. Registering an instance is what let one game's shutdown break the next.
        context.registerService(PulseService.KEY, PulseService::new);

        context.buildings().register(GENERATOR_ID, new BuildingPrototype(
                GENERATOR_ID,
                "Pulse Generator",
                new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.MINER, // borrowed art: this fixture is about behavior, not pixels
                1, 1,
                0, 1, false,
                (self, direction, factory) ->
                        new PulseGenerator(self.id(), direction, factory.items().get(PULSE_ITEM_ID)),
                (self, decodedState, factory) -> {
                    PulseGenerator.PulseState state = (PulseGenerator.PulseState) decodedState;
                    return new PulseGenerator(self.id(), state.direction(),
                            factory.items().get(PULSE_ITEM_ID), state.produced());
                },
                CODEC));
    }
}
