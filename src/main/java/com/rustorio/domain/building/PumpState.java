package com.rustorio.domain.building;

import com.rustorio.domain.Direction;

/**
 * {@link Pump}'s own captured state — which way it faces and how far into its current cycle it is.
 * Nothing about the fluid: a pump holds none (it lifts straight from the map into the network), and
 * WHICH fluid it lifts is its prototype's {@code fluidOutput}, not per-instance state.
 */
public record PumpState(Direction direction, int cooldown) {
}
