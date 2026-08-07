package com.webminer;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link Interpreter}'s own captured state — {@code fields} is the player's chosen list of JSON
 * field names to pull out of whatever {@link TickContext#lastResponseBody} shows for the cell
 * behind it (set via {@link Interpreter#setFields}, same "plain data the player picks" shape {@link
 * FilterState#filterItem} already has). A {@link List}, not a {@link java.util.Set}: field order is
 * exactly what the inspection panel displays top to bottom, and the player typed it in that order.
 */
public record InterpreterState(Direction direction, @Nullable ItemType held, List<String> fields) {
}
