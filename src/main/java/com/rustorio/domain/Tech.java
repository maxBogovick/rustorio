package com.rustorio.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A global technology: costs research points, once unlocked applies everywhere on the map (as
 * opposed to a per-building {@code speedLevel} upgrade).
 *
 * <p><b>Owner decision (P-02, DEV_TASKS.md):</b> {@link #prerequisites} turns the tech list into an
 * actual tree — {@link Research#unlock} refuses a tech until every one of its prerequisites is
 * already unlocked, not just when its own cost is covered. A constant may only list EARLIER
 * constants as prerequisites (enforced by the language itself, not a runtime check): an enum
 * constant's constructor runs before any LATER constant exists, so a forward reference would be a
 * compile error — which conveniently makes a prerequisite cycle structurally impossible here,
 * without needing a graph-cycle test the way {@code RecipeBook} does (S-03).
 *
 * <p>The shape below is this task's own balance call, not derived from the audit (which names no
 * structure): {@code FAST_MINING} is the one root; {@code FAST_SMELTING} and {@code BIG_BUFFER}
 * branch independently off it; {@code LONG_TUNNEL} extends the buffer branch; {@code FAST_LAB} sits
 * at the top, requiring BOTH branches — an actual tree shape, not a straight line re-labeled as one
 * (the flat, order-only list this task's card explicitly calls out as the thing to fix).
 *
 * <p><b>Owner decision (N15, NEW_BUGS_PROGRESS.md):</b> every cost here is exactly ten times what it
 * was (8/20/35/55/80 → 80/200/350/550/800). The numbers themselves carry no new balance intent — they
 * track {@code Lab.POINTS_PER_GEAR}, which went from 1 to 10 so that a batch's points can express
 * "45% deeper than a GEAR" instead of rounding it away. Leaving the costs alone while the income per
 * batch grew tenfold would have collapsed the whole tree into roughly one lab batch per tech, which
 * is the same "no real tradeoff" problem P-02 exists to avoid.
 */
public enum Tech {
    FAST_MINING(80, "Fast mining"),
    FAST_SMELTING(200, "Fast smelting", FAST_MINING),
    BIG_BUFFER(350, "Big buffers", FAST_MINING),
    LONG_TUNNEL(550, "Long tunnels", BIG_BUFFER),
    FAST_LAB(800, "Fast research", FAST_SMELTING, BIG_BUFFER);

    private final int cost;
    private final String label;
    private final Set<Tech> prerequisites;

    Tech(int cost, String label, Tech... prerequisites) {
        this.cost = cost;
        this.label = label;
        // LinkedHashSet, not Set.of(...) (S2, CODE_REVIEW_2026-07-28.md): Set.of's iteration order
        // is deliberately randomized per JVM run (a salt in java.util.ImmutableCollections) —
        // harmless for Research#unlock, which only ever calls contains(), but TechTreeRenderer
        // iterates this set to print "(needs: ...)" and would print prerequisites in a different
        // order every time the game launches. LinkedHashSet preserves insertion order, i.e. the
        // order prerequisites were listed in the constant's own declaration, deterministically.
        // NOT EnumSet: EnumSet.noneOf(Tech.class), called from THIS constructor while Tech itself
        // is still running its static initializer for its very first constant, throws
        // ClassCastException("Tech not an enum") — Class.getEnumConstantsShared() isn't populated
        // yet at that point, a JVM self-reference quirk with no clean workaround short of avoiding
        // EnumSet here entirely.
        Set<Tech> ordered = new LinkedHashSet<>();
        Collections.addAll(ordered, prerequisites);
        this.prerequisites = Collections.unmodifiableSet(ordered);
    }

    public int cost() {
        return cost;
    }

    public String label() {
        return label;
    }

    /** Every tech that must already be unlocked before this one can be — empty for a root like {@code FAST_MINING}. Iterates in declaration order (see the constructor). */
    public Set<Tech> prerequisites() {
        return prerequisites;
    }
}
