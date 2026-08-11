package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link Chest}'s own captured state. {@code contents} is wrapped in a {@link TreeMap}, not {@code
 * Map.copyOf}: the latter's iteration order is deliberately randomized per JVM run for maps with
 * more than one entry — {@link ItemType}'s own {@code Comparable} (by {@code ContentId}) keeps
 * this deterministic instead.
 */
public record ChestState(Direction direction, Map<ItemType, Integer> contents, int speedLevel) {
    public ChestState {
        contents = Collections.unmodifiableMap(new TreeMap<>(contents));
    }
}
