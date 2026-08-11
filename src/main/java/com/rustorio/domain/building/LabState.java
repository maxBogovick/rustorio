package com.rustorio.domain.building;

import com.rustorio.api.content.model.ItemType;
import java.util.List;

/** {@link Lab}'s own captured state — {@code buffer} keeps each queued item's own identity, not just a count (needed to award points proportional to that specific item's research depth). */
public record LabState(List<ItemType> buffer, int cooldown, int speedLevel) {
    public LabState {
        buffer = List.copyOf(buffer);
    }
}
