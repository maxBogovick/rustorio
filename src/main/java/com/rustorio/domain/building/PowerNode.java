package com.rustorio.domain.building;

import org.jspecify.annotations.Nullable;

/**
 * A pole: the only kind of building that is actually a MEMBER of a {@link PowerNetwork}. Generators
 * and machines are not members — they are merely covered by a pole, which is what lets a factory be
 * rewired by moving one pole instead of by rebuilding everything around it.
 *
 * <p>The fluid counterpart is {@link FluidNode}, and the shape is deliberately the same so that a
 * mod's own pole is possible for the same reason its own pipe is. What differs is what "connected"
 * means: pipes touch side to side, poles reach across a gap, so a network here is a component of
 * poles within reach of each other rather than of tiles sharing an edge.
 */
public interface PowerNode {

    /** How far this pole reaches, in cells — both for covering machines and for finding other poles. */
    int coverageRadius();

    /** The network this pole belongs to — {@code null} while it is unplaced or demolished. */
    @Nullable PowerNetwork network();

    /** Join (or leave, with {@code null}) a network — called only by {@link PowerNetwork} itself. */
    void joinNetwork(@Nullable PowerNetwork network);
}
