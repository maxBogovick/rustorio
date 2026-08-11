package com.lightbulb;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TraitKey;
import com.rustorio.domain.building.Traits;
import com.rustorio.domain.building.VanillaCategories;
import com.rustorio.domain.building.VanillaPlacementRules;
import com.rustorio.domain.building.VanillaTraits;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code half of the lightbulb educational mod: only the placeable lamp needs Java. Items, crafter
 * buildings, recipes and the workshop map live in {@code content/} and load before this entry
 * point — same split as petrochem.
 *
 * <p>Wire drawer and base former stay data-only (private recipe pools on PRESS archetypes) so their
 * steps appear in the recipe book. The lamp cannot: no vanilla archetype is a pure power consumer
 * that swaps sprites when powered.
 *
 * <p>Placing a lamp costs one crafted {@code lightbulb} item, so the factory loop closes: ore →
 * parts → bulb in inventory → building that burns when powered.
 */
public final class LightbulbMod implements RustorioMod {

    public static final ContentId LAMP_ID = ContentId.of("lightbulb:lamp");

    private static final ContentId LIGHTBULB_ITEM = ContentId.of("lightbulb:lightbulb");

    /** Small draw: one vanilla generator (100) can keep many lamps lit for the lesson. */
    private static final int POWER_DEMAND = 5;

    private static final Codec<Lamp.LampState> CODEC = new Codec<>() {
        @Override
        public Object encode(Lamp.LampState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            return data;
        }

        @Override
        public Lamp.LampState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new Lamp.LampState(Direction.valueOf(Codec.requireField(data, "direction")));
        }
    };

    @Override
    public void registerContent(RegistrationContext context) {
        ItemType lightbulb = context.items().peek(LIGHTBULB_ITEM).orElseThrow(() -> new IllegalStateException(
                LIGHTBULB_ITEM + " missing: content/items must load before this entry point"));

        context.buildings().register(LAMP_ID, new BuildingPrototype(
                LAMP_ID,
                "Lamp",
                new BuildingCost(lightbulb, 1),
                context.placementRules().peek(VanillaPlacementRules.NEEDS_PASSABLE_TERRAIN).orElseThrow(),
                Lamp.SPRITE_OFF,
                1, 1,
                0, 1, false,
                (self, direction, factory) -> new Lamp(self, direction),
                (self, decodedState, factory) -> {
                    Lamp.LampState state = (Lamp.LampState) decodedState;
                    return new Lamp(self, state.direction());
                },
                CODEC,
                LAMP_ID,
                null,
                Traits.of(traits())));
    }

    private static Map<TraitKey<?>, Object> traits() {
        Map<TraitKey<?>, Object> traits = new LinkedHashMap<>();
        traits.put(VanillaTraits.POWER, PowerSpec.consumer(POWER_DEMAND));
        traits.put(VanillaCategories.CATEGORY, VanillaCategories.POWER);
        return traits;
    }
}
