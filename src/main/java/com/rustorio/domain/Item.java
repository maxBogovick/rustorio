package com.rustorio.domain;

/**
 * Every item that can exist in the game — raw ore through the most refined intermediate goods.
 *
 * <p>{@code researchable} marks the top-tier goods {@code Lab} spends on research points (P2-08,
 * BUG_FIX_PROGRESS.md). Living on the enum, not a hardcoded list in {@code Lab.accept}, means a new
 * item that should feed the lab is a one-word change here, not a change to {@code Lab} — and a new
 * item that DOESN'T belong (raw ore, an intermediate plate) simply defaults to {@code false} instead
 * of silently working because nobody remembered to add it to a check somewhere else.
 */
public enum Item {
    IRON_ORE(false),
    IRON_PLATE(false),
    GEAR(true),
    BRONZE_ORE(false),
    BRONZE_PLATE(false),
    MECHANISM(true),
    ENGINE(true),
    CHASSIS(true),
    ALLOY_PLATE(false),
    ALLOY_GEAR(true);

    private final boolean researchable;

    Item(boolean researchable) {
        this.researchable = researchable;
    }

    /** Whether {@code Lab} accepts this item — see the class javadoc. */
    public boolean isResearchGrade() {
        return researchable;
    }
}
