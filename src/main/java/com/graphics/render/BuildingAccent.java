package com.graphics.render;

import com.rustorio.domain.building.BuildingPrototype;

/**
 * Which system a building belongs to — fluid, power, or neither — so the renderer can mark it with a
 * thin accent stripe in that category's colour.
 *
 * <p>What the stripe is FOR: a pump and a boiler look like machines, and nothing else on the tile
 * says which of them is plumbing and which is wired. Membership in a system is not otherwise
 * visible at a glance — a pipe gets away without a stripe only because its joints and fill bar
 * already announce it. (An earlier draft justified the stripe by every new building borrowing
 * another's placeholder sprite; they have their own sprites now, so that reason has expired while
 * the stripe's real one has not.)
 *
 * <p><b>Read from data, not from the building's class.</b> The category is decided by what the
 * prototype declares — a power spec, a fluid port — exactly the way {@code OverlayRenderer} spots a
 * pole by its {@code radius} rather than by {@code instanceof Pole}. So a mod's own pump or turbine
 * is categorised correctly with no change here, and no {@code switch} over building kinds is added
 * for the content-coupling ratchet to count. Pure and windowless on purpose (the {@code
 * graphics.md} rule): the mapping is tested, even though the pixels can only be seen with a window.
 */
enum BuildingAccent {

    /** Belts, chests, furnaces, pipes, tanks — no mark; a pipe already reads as fluid from its joints and fill bar. */
    NONE,
    /** Anything electrical: a pole, a generator, or a machine that draws power (it declares a {@code PowerSpec}). */
    POWER,
    /** A fluid machine that is not a plain pipe or tank: it names an input or output fluid port (a pump, a boiler). */
    FLUID;

    /**
     * Power wins when a building is both (a generator declares an output AND burns a fluid) — it is
     * fundamentally the electrical end of the chain, and one building gets one stripe.
     */
    static BuildingAccent forPrototype(BuildingPrototype prototype) {
        if (prototype.power() != null) {
            return POWER;
        }
        if (prototype.fluidInput() != null || prototype.fluidOutput() != null) {
            return FLUID;
        }
        return NONE;
    }
}
