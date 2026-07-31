package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import org.jspecify.annotations.Nullable;

/** {@link Filter}'s own captured state — {@code filterItem}: the player's chosen pass-through item (see {@link Filter#cycleFilterItem}). */
public record FilterState(Direction facing, @Nullable ItemType held, ItemType filterItem) {
}
