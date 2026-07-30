package com.rustorio.domain.building;

/**
 * A building that tracks "received cargo earlier this same world tick" and must forget that mark
 * once, before either traversal pass runs — {@link Belt}, {@link UndergroundBelt}, {@link
 * Splitter}, {@link Filter}, {@link Inserter} each keep their own {@code arrivedThisTick} field for
 * exactly this reason (see {@link Belt#arrivedThisTick} for the full rationale, P2-07/P3-03).
 */
interface SettlesEachTick {

    /** Called once per world tick, before any building ticks — see {@code TickScheduler}. */
    void clearArrivalMark();
}
