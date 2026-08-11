package com.rustorio.domain.building;

import com.rustorio.api.content.model.FluidType;
import org.jspecify.annotations.Nullable;

/**
 * One tile of a {@link FluidNetwork} — the fluid counterpart to {@link TransportNode}, and a
 * moddable capability for the same reason: a class living outside this package can implement it and
 * join a network on equal footing with the vanilla {@link Pipe}.
 *
 * <p><b>A tile has fluid of its own only while it is NOT in a network.</b> Placed, it owns nothing:
 * the network is one bucket and holds the whole volume (see {@link FluidNetwork}). The two
 * "detached" accessors below cover the two moments where a tile is between networks — restored from
 * a save with its own share not yet poured into anything, and demolished, carrying its share away
 * with it, exactly the way a demolished belt tile takes its cargo instead of leaking it onto a
 * neighbor. Outside those two moments they are {@code null}/{@code 0}.
 */
public interface FluidNode {

    /** How much this ONE tile adds to its network's capacity — a pipe is small, a tank is not. */
    long fluidCapacity();

    /** The network this tile currently belongs to — {@code null} while it is unplaced or demolished. */
    @Nullable FluidNetwork network();

    /** Join (or leave, with {@code null}) a network — called only by {@link FluidNetwork} itself. */
    void joinNetwork(@Nullable FluidNetwork network);

    /** The fluid this tile carries while outside a network — see the interface javadoc. */
    @Nullable FluidType detachedFluid();

    /** How much of {@link #detachedFluid()} this tile carries while outside a network. */
    long detachedAmount();

    /**
     * Set what this tile carries while outside a network — {@link FluidNetwork} calls this with
     * {@code (null, 0)} when it absorbs the tile's share on placement, and with the tile's own
     * share when the tile is removed.
     */
    void setDetached(@Nullable FluidType fluid, long amount);
}
