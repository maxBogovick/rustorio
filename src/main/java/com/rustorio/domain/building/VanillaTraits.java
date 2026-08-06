package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.FluidType;

/**
 * The optional building properties the base game ships. A mod declares its own {@link TraitKey}
 * exactly like these and nothing treats the vanilla ones as special — that is the whole point of
 * {@link Traits}.
 *
 * <p>The {@code dataKey} of each is the name it has always had in a content file, so every JSON
 * building written before traits existed still loads unchanged.
 */
public final class VanillaTraits {

    /**
     * Which fluid a machine pulls in — the reason {@link Pump} contains no mention of water and
     * {@link Boiler} none of steam, so a mod's own oil derrick is a JSON file on the same archetype.
     */
    public static final TraitKey<FluidType> FLUID_INPUT =
            new TraitKey<>(ContentId.of("rustorio:fluid_input"), "fluidInput", FluidType.class);

    /** Which fluid a machine pushes out — the counterpart to {@link #FLUID_INPUT}. */
    public static final TraitKey<FluidType> FLUID_OUTPUT =
            new TraitKey<>(ContentId.of("rustorio:fluid_output"), "fluidOutput", FluidType.class);

    /**
     * What this building has to do with electricity, as one grouped {@link PowerSpec} rather than
     * three loose numbers: "nothing to do with electricity" stays a single absent trait, which is
     * what almost every building is and what keeps electricity opt-in.
     */
    public static final TraitKey<PowerSpec> POWER =
            new TraitKey<>(ContentId.of("rustorio:power"), "power", PowerSpec.class);

    private VanillaTraits() {
    }
}
