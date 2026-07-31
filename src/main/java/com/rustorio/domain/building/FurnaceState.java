package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link Furnace}'s own captured state — no {@code kind} or {@code prototypeId} here, unlike the
 * old (now-removed) {@code BuildingMemento.FurnaceState}: both are resolved BEFORE this state is
 * even decoded — the save's own envelope names the exact governing prototype directly, and that
 * prototype's own registered restore behavior already knows which {@code BuildingType} kind to
 * build (see {@code VanillaBuildings}) — so carrying either one inside the state itself would just
 * be redundant duplication of data the envelope already has.
 *
 * <p>{@code buffers} — one entry per {@code Recipe.ingredients()}, in the same order, empty when no
 * recipe is committed yet. {@code fuelBuffer} — coal on hand, always {@code 0} for a kind with no
 * fuel concept. {@code selectedRecipeOutput} — the player's STANDING preference among ambiguous
 * recipes, separate from {@code recipeOutput} (the currently COMMITTED batch).
 */
public record FurnaceState(
        Direction direction,
        List<Integer> buffers,
        int cooldown,
        @Nullable ItemType recipeOutput,
        @Nullable ItemType pendingOutput,
        int fuelBuffer,
        @Nullable ItemType selectedRecipeOutput,
        int speedLevel) {
    public FurnaceState {
        buffers = List.copyOf(buffers);
    }
}
