package com.rustorio.domain;

/**
 * Strategy pattern: where a splitter sends an item — forward ({@code true}) or to the rotated
 * side ({@code false}). Kept independent of the {@code Splitter} building itself so a different
 * splitter could be built with a different rule without touching the {@code Splitter} class.
 *
 * <p><b>Owner decision (P4-10, BUG_FIX_PROGRESS.md):</b> option (A) — persist the rule properly,
 * by {@link #id()}, instead of leaving {@code Splitter} to always reload as {@link #ORE_FORWARD}
 * regardless of what was actually built. {@code ORE_FORWARD} is still the only rule the game
 * ships with; the point of this change is that a second one, whenever it's added, survives
 * save/load for free instead of needing its own persistence plumbing.
 */
public interface SortRule {

    boolean forward(Item item);

    /** The identifier this rule is saved/restored under — see {@code BuildingMemento.SplitterState}. */
    String id();

    /** Default rule: ore goes forward, everything else (plates and beyond) goes to the side. */
    SortRule ORE_FORWARD = new SortRule() {
        @Override
        public boolean forward(Item item) {
            return item == Item.IRON_ORE || item == Item.BRONZE_ORE;
        }

        @Override
        public String id() {
            return "ORE_FORWARD";
        }
    };

    /**
     * The rule a persisted {@code id} refers to — the inverse of {@link #id()}. Throws on an
     * unrecognized id rather than silently substituting a default: the same "fail loud, don't
     * guess" choice {@code JsonSaveRepository} already makes for a semantically broken save (see
     * P1-03, BUG_FIX_PROGRESS.md) — this exception propagates through {@code
     * BuildingFactory.restore} into that same phase-1 safety net.
     */
    static SortRule byId(String id) {
        if (ORE_FORWARD.id().equals(id)) {
            return ORE_FORWARD;
        }
        throw new IllegalArgumentException("Unknown SortRule id: " + id);
    }
}
