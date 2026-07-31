package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import org.jspecify.annotations.Nullable;

/** {@link Belt}'s own captured state — not speed-eligible, so no {@code speedLevel} field. */
public record BeltState(Direction direction, @Nullable ItemType held) {
}
