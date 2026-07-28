package com.rustorio.domain;

/**
 * The depletion rule every {@link OreLayout} applies per cell, shared rather than duplicated in
 * each implementation so the two tuning constants below can't silently drift apart between the two
 * map generators (D-04, DEV_TASKS.md).
 *
 * <p>Deliberately deterministic, not probabilistic: {@link OreLayout#extract} is called from
 * {@code Miner.tick}, and the whole simulation must stay reproducible from a fixed seed (see
 * {@code WorldReplayTest}, S-01) — a hidden {@code Random} in here would quietly break that.
 * Counting every {@link #yields} call (successful or not), not just the successful ones, matters
 * for the same reason a stopped clock is still wrong twice a day: if only successes advanced the
 * count, a cell would freeze the instant it first failed in the tail (the count would never move
 * again to satisfy the next modulo hit) and never yield again.
 */
final class OreDepletion {

    /**
     * Calls a cell yields ore on before entering its tail — roughly five real-time minutes of
     * continuous mining at {@code Miner}'s default {@code MINE_TIME} of 3 ticks per batch. "Large"
     * per the audit's "large reserve, thin infinite tail" model (§2.2, В3), not meant to be reached
     * in a short play session.
     */
    static final int RICHNESS = 6000;

    /** Once depleted, only every {@value}th call still yields — a steady 1/{@value} of the fresh rate, forever. */
    static final int TAIL_INTERVAL = 10;

    private OreDepletion() {
    }

    /**
     * Whether the {@code callsSoFar}-th call (0-indexed, the count BEFORE this call) to {@link
     * OreLayout#extract} on one cell succeeds.
     */
    static boolean yields(int callsSoFar) {
        return callsSoFar < RICHNESS || (callsSoFar - RICHNESS) % TAIL_INTERVAL == 0;
    }
}
