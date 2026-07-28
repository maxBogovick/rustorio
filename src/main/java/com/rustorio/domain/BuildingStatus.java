package com.rustorio.domain;

/**
 * What's actually happening inside a building right now — the fix for §3.3/§6.5 of the design
 * audit (F-01, DEV_TASKS.md): {@code Miner.tick} idling on an ore-less tile used to be a silent
 * {@code return;} with no visible difference from a miner whose OUTPUT was simply blocked — on
 * screen, a stalled machine and a healthy one looked identical. A building computes and caches its
 * own status once per {@link com.rustorio.domain.building.Building#tick}, not once per render
 * frame (see that method's own javadoc for why) — {@link Appearance} just carries whatever the
 * building last decided.
 */
public enum BuildingStatus {
    /** Nothing wrong — producing, or on its way to producing, at its normal pace. */
    WORKING,
    /** Waiting on an ingredient that hasn't arrived (a furnace with no ore buffered at all, say). */
    NO_INPUT,
    /** A miner sitting on a cell with no ore to extract — the exact silent failure §3.3 names. */
    NO_ORE,
    /** A {@code FURNACE} with ingredients buffered but no coal to burn (D-05). */
    NO_FUEL,
    /** Finished a batch (or a chest reached capacity) but the neighbor it needs to hand off to won't take it. */
    OUTPUT_FULL
}
