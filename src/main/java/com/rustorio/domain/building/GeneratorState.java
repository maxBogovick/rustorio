package com.rustorio.domain.building;

import com.rustorio.domain.Direction;

/**
 * {@link Generator}'s own captured state: which side it draws its fuel fluid from, and nothing else.
 * Power is not storable (see {@link PowerNetwork}), so there is no charge to write down — a
 * generator reloaded mid-run simply starts producing again on its first tick, from whatever steam
 * the pipe next to it holds.
 */
public record GeneratorState(Direction direction) {
}
