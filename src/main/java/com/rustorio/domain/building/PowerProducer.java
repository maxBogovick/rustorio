package com.rustorio.domain.building;

/**
 * A building that puts power into whatever grid covers it — a capability, like {@link
 * TransportNode} and {@link FluidNode}, so a mod's own solar panel needs nothing from this package
 * but this one method.
 *
 * <p>A producer is not a MEMBER of a {@link PowerNetwork} (only poles are — see that class). {@code
 * World} keeps the list of them and, once per tick BEFORE any building ticks, asks each one for its
 * output and hands it to whichever network covers that cell. Which means a producer never has to
 * track what grid it is on, and a machine never runs on power that has not been generated yet.
 */
public interface PowerProducer {

    /**
     * Produce this tick's power and return how much — called exactly once per tick, at a point
     * where nothing else has ticked yet, which is where a generator consumes whatever it burns.
     * Returning {@code 0} is the ordinary way to say "nothing to burn", not a failure.
     */
    long produce(TickContext world, int x, int y);
}
