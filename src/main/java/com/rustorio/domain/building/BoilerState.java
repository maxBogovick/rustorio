package com.rustorio.domain.building;

import com.rustorio.domain.Direction;

/**
 * {@link Boiler}'s own captured state: which way it faces, how much fuel is stacked up, and how much
 * of the current piece of fuel is still burning. No fluid of its own — a boiler buffers none, it
 * moves fluid straight from the network behind it to the one in front within a single tick.
 *
 * <p>{@code burnTicksLeft} is saved rather than reset on load for the same reason a furnace's
 * cooldown is: a machine that silently restarted its current piece of fuel every time the player
 * reloaded would be a free fuel generator.
 */
public record BoilerState(Direction direction, int fuelBuffer, int burnTicksLeft) {
}
