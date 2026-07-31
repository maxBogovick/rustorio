package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import org.jspecify.annotations.Nullable;

/** {@link Splitter}'s own captured state — {@code nextIsForward}: which side gets the NEXT delivered item (strict round-robin, no rule to persist). */
public record SplitterState(Direction facing, @Nullable ItemType held, boolean nextIsForward) {
}
