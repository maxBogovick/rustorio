package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import org.jspecify.annotations.Nullable;

/** {@link UndergroundBelt}'s own captured state — {@code kind} (IN/OUT) is genuine per-instance state, not something a governing prototype could infer, since both halves share one archetype class. */
public record UndergroundBeltState(UndergroundBelt.Kind kind, Direction direction, @Nullable ItemType held) {
}
