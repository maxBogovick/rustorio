package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    record MinerState(Direction direction, int cooldown, @Nullable Item held) implements BuildingMemento {
    }

    /**
     * {@code contents} carries per-kind counts now, not one blind total (D-02, DEV_TASKS.md — §2.5
     * of the design audit: the old chest threw away item identity on {@code accept}).
     *
     * <p>Wrapped in an {@link EnumMap}, not {@code Map.copyOf}: the latter's iteration order is
     * deliberately randomized per JVM run for maps with more than one entry (see {@code
     * java.util.ImmutableCollections}'s salt) — the same trap {@code WorldReplayTest} (S-01) had to
     * route around for {@code ProductionStats.Snapshot}'s totals map. This record's default {@code
     * toString()} is exactly what feeds that replay test's canonical state string, so a randomized
     * order here would make the "same input, same hash" guarantee quietly false.
     */
    record ChestState(Direction direction, Map<Item, Integer> contents) implements BuildingMemento {
        public ChestState {
            // new EnumMap<>(Map) throws ClassCastException when the argument is empty and isn't
            // itself an EnumMap (it can't infer the key type from zero entries — Jackson hands one
            // of these for every freshly-placed, still-empty chest it deserializes). Same issue
            // Research.Snapshot's EnumSet already solved: build an empty EnumMap with an explicit
            // key type, then copy into it, instead of asking EnumMap to copy blind.
            Map<Item, Integer> copy = new EnumMap<>(Item.class);
            copy.putAll(contents);
            contents = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * {@code fuelBuffer} — coal on hand (D-05, DEV_TASKS.md); always 0 for a {@code PRESS}, which
     * has no fuel concept. {@code selectedRecipeOutput} — the player's STANDING preference among
     * ambiguous recipes (F-03, DEV_TASKS.md; see {@code Furnace#selectedRecipe}'s own javadoc for
     * why it's a separate field from {@code recipeOutput}, which is the currently COMMITTED batch)
     * — {@code @Nullable} both for "no preference" and so a save written before this field existed
     * still deserializes (Jackson defaults a missing reference-typed field to {@code null}).
     */
    record FurnaceState(
            BuildingType kind,
            Direction direction,
            int bufferA,
            int bufferB,
            int cooldown,
            @Nullable Item recipeOutput,
            @Nullable Item pendingOutput,
            int fuelBuffer,
            @Nullable Item selectedRecipeOutput) implements BuildingMemento {
    }

    record BeltState(Direction direction, @Nullable Item held) implements BuildingMemento {
    }

    /**
     * {@code nextIsForward} — which side {@link Splitter} sends its NEXT delivered item to (X-01,
     * DEV_TASKS.md). Replaces the old {@code rule} field (a persisted {@code SortRule} id, P4-10) —
     * {@link Splitter} no longer has a rule at all, it's a strict round-robin now; the OTHER half
     * of the old combined building, {@link Filter}'s player-chosen item, has its own {@link
     * FilterState}.
     */
    record SplitterState(Direction facing, @Nullable Item held, boolean nextIsForward) implements BuildingMemento {
    }

    /** {@code filterItem} — the player's choice via {@link Filter#cycleFilterItem} (X-01, DEV_TASKS.md): what passes forward: everything else goes to the rotated side. */
    record FilterState(Direction facing, @Nullable Item held, Item filterItem) implements BuildingMemento {
    }

    /** (X-01, DEV_TASKS.md) A single-cell direct-transfer building — see {@link Inserter}'s own javadoc for why it's mechanically a one-tile {@link Belt}. */
    record InserterState(Direction direction, @Nullable Item held) implements BuildingMemento {
    }

    /**
     * {@code buffer} carries each queued item's OWN identity now, not just how many (P-01,
     * DEV_TASKS.md) — {@link Lab} awards points per finished batch proportional to that specific
     * item's {@code RecipeBook.depthOf}, which needs to know which item is which, not only a count.
     */
    record LabState(List<Item> buffer, int cooldown) implements BuildingMemento {
        public LabState {
            buffer = List.copyOf(buffer);
        }
    }

    record UndergroundBeltState(UndergroundBelt.Kind kind, Direction direction, @Nullable Item held)
            implements BuildingMemento {
    }
}
