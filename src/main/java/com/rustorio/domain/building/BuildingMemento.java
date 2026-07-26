package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import org.jspecify.annotations.Nullable;

/**
 * Memento pattern: a building's entire internal state, captured as one immutable record, with no
 * knowledge of files or text formats. {@link Building#memento()} produces one of these; {@link
 * BuildingFactory#restore} turns one back into a live building.
 *
 * <p>Replaces the earlier hand-rolled {@code save()/load(String)} pair: instead of every building
 * inventing and parsing its own space-separated line, the persistence layer (Jackson) serializes
 * these records directly — one {@code @JsonSubTypes} entry per building kind, no manual string
 * splitting, no silent field-count mismatches.
 *
 * <p>Fields are plain nullable types, not {@code Optional<Item>}: Jackson (without the extra
 * jdk8-datatypes module this project doesn't depend on) doesn't serialize {@code Optional}, and
 * Effective Java Item 55 says a field/record-component is the wrong place for it anyway —
 * {@code Optional} belongs on method return types, which is exactly where {@link Building}'s
 * public accessors (like {@link Building#heldItem()}) already put it.
 */
public sealed interface BuildingMemento {

    record MinerState(int cooldown, @Nullable Item held) implements BuildingMemento {
    }

    record ChestState(int count) implements BuildingMemento {
    }

    record FurnaceState(
            BuildingType kind,
            Direction direction,
            int bufferA,
            int bufferB,
            int cooldown,
            @Nullable Item recipeOutput,
            @Nullable Item pendingOutput) implements BuildingMemento {
    }

    record BeltState(Direction direction, @Nullable Item held) implements BuildingMemento {
    }

    /**
     * {@code rule} is the persisted {@code SortRule}'s id (P4-10, BUG_FIX_PROGRESS.md — owner
     * decision A) — {@code @Nullable} so a save written before this field existed still loads;
     * {@code null} means "unknown, assume the default" (see {@code BuildingFactory#restore}).
     */
    record SplitterState(Direction facing, @Nullable Item held, @Nullable String rule) implements BuildingMemento {
    }

    record LabState(int buffer, int cooldown) implements BuildingMemento {
    }

    record UndergroundBeltState(UndergroundBelt.Kind kind, Direction direction, @Nullable Item held)
            implements BuildingMemento {
    }
}
