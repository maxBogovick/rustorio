package com.rustorio.domain;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A furnace/press recipe: what goes in, what comes out, how long it takes, and which {@link
 * BuildingType} role runs it. Pure data — see {@link RecipeBook} for the registry that furnaces
 * search through (Open/Closed principle: a new recipe is a new constant there, never a change to
 * {@code Furnace} itself).
 *
 * <p>{@code input2} is deliberately a plain, possibly-{@code null} field rather than an {@code
 * Optional<Item>} component: Effective Java (Item 55) is explicit that {@code Optional} should
 * never be a field or record-component type, only a method return type. {@link #secondInput()}
 * is the Optional-returning view for callers who want it; {@link #input2()} (raw, nullable) stays
 * for the tight hot-path comparisons inside {@code Furnace}.
 */
public record Recipe(Item input, @Nullable Item input2, Item output, int time, BuildingType type) {

    /** Single-input recipe — every step except {@link RecipeBook#ENGINE}. */
    public Recipe(Item input, Item output, int time, BuildingType type) {
        this(input, null, output, time, type);
    }

    /** Optional-returning view of the second input, for call sites that prefer it over {@code null}. */
    public Optional<Item> secondInput() {
        return Optional.ofNullable(input2);
    }

    public boolean hasSecondInput() {
        return input2 != null;
    }
}
