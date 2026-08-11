package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import org.jspecify.annotations.Nullable;

/**
 * {@link Miner}'s own captured state, paired with its own {@link Codec} (see {@code
 * VanillaBuildings}). {@code speedLevel} lives here now, as a plain field of the state itself,
 * not as an external parameter to {@code BuildingFactory.restore} the way it did before this
 * phase — state becomes properly flat, no more out-of-band pieces.
 */
public record MinerState(Direction direction, int cooldown, @Nullable ItemType held, int speedLevel) {
}
