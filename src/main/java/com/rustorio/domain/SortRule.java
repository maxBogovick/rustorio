package com.rustorio.domain;

/**
 * Strategy pattern: where a splitter sends an item — forward ({@code true}) or to the rotated
 * side ({@code false}). Kept independent of the {@code Splitter} building itself so a different
 * splitter could be built with a different rule without touching the {@code Splitter} class.
 */
@FunctionalInterface
public interface SortRule {

    boolean forward(Item item);

    /** Default rule: ore goes forward, everything else (plates and beyond) goes to the side. */
    SortRule ORE_FORWARD = item -> item == Item.IRON_ORE || item == Item.BRONZE_ORE;
}
