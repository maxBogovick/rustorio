package com.rustorio.domain.building;

import com.rustorio.api.content.model.FluidType;
import org.jspecify.annotations.Nullable;

/**
 * A handle onto one fluid network, as seen from a building standing next to it — what {@link
 * TickContext#fluidPort} hands out. A machine pushes fluid in or pulls it out through this and
 * never learns anything else about the network: not its size, not its shape, not which tiles it is
 * made of.
 *
 * <p>Deliberately as narrow as {@link TickContext} itself is for the world: {@link FluidNetwork} is
 * the only implementation and stays the concrete type inside this package, but a boiler has no
 * business reaching a network's tile map, so it is handed this instead.
 *
 * <p>{@link #insert}/{@link #extract} return how much actually moved rather than a boolean,
 * because a partial transfer is the normal case: a network with room for 30 more units, offered
 * 100, takes 30. A caller that needs all-or-nothing checks {@link #amount}/{@link #capacity} first
 * — this interface will not roll a partial move back for it.
 */
public interface FluidPort {

    /** Which fluid this network currently holds — {@code null} when it is empty, and an empty network accepts any fluid. */
    @Nullable FluidType fluid();

    /** How much fluid the network holds right now, across every one of its tiles. */
    long amount();

    /** How much it could hold when full — the sum of its tiles' own capacities. */
    long capacity();

    /**
     * Push up to {@code amount} units of {@code fluid} in.
     *
     * @return how much was taken: {@code 0} if the network already holds a different fluid or is
     *         full, otherwise up to {@code amount}
     */
    long insert(FluidType fluid, long amount);

    /**
     * Pull up to {@code amount} units of {@code fluid} out.
     *
     * @return how much came out: {@code 0} if the network is empty or holds something else,
     *         otherwise up to {@code amount}
     */
    long extract(FluidType fluid, long amount);
}
