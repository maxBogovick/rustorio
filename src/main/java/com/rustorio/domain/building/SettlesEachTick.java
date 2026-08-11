package com.rustorio.domain.building;

/**
 * A building that tracks "received cargo earlier this same world tick" and must forget that mark
 * once, before either traversal pass runs — {@link Belt}, {@link UndergroundBelt}, {@link
 * Splitter}, {@link Filter}, {@link Inserter}, {@link Chest} each keep their own {@code
 * arrivedThisTick} field for exactly this reason (see {@link Belt#arrivedThisTick} for the full
 * rationale).
 *
 * <p>Public since the phase's own acceptance capstone: kept package-private at first (only
 * {@code BuildingFactory.clearArrivalMark}, same package, ever needed to name it), but a mod's own
 * transport node needs it too — {@code TickScheduler} clears arrival marks by {@code instanceof
 * SettlesEachTick}, and a foreign class can't implement an interface it can't even name. See
 * {@link TransportNode}'s own javadoc for the matching {@link BeltSegment} story.
 */
public interface SettlesEachTick {

    /** Called once per world tick, before any building ticks — see {@code TickScheduler}. */
    void clearArrivalMark();
}
