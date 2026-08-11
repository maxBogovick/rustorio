package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import org.jspecify.annotations.Nullable;

/** {@link Inserter}'s own captured state — mechanically a one-tile {@link Belt}, not speed-eligible. */
public record InserterState(Direction direction, @Nullable ItemType held) {
}
