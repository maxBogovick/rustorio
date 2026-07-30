package com.rustorio.domain.world;

import com.rustorio.domain.ItemType;

/**
 * Observer pattern: notified whenever an item is produced anywhere on the map. {@link World}
 * knows only that each subscriber can answer "an item was produced, here it is" — not how many
 * there are or what they do with it.
 *
 * <p><b>Owner decision (D-06, DEV_TASKS.md):</b> {@code tick} is the world's own tick counter at
 * the moment of production — {@link World#tick()} incremented it, not real time. This is a
 * breaking interface change (there used to be no time axis at all: {@code onProduced(ItemType)}), made
 * deliberately while reworking {@link ProductionStats} rather than bolted on later, per the design
 * audit's §4.1 warning that "sht/min" (items per minute) is uncomputable without one.
 *
 * <p>Ticks, never {@code System.nanoTime()}/wall-clock seconds: the world steps at a fixed logical
 * rate ({@code TICK_SECONDS = 1/60}), but the player controls a 1×/2×/4× speed multiplier and
 * pause ({@code SimulationControls}). A real-time window would read quadruple on 4× and freeze at
 * zero on pause even with a perfectly healthy factory; a tick is the one unit that means the same
 * thing regardless of how fast the player is running the simulation or how often a frame renders.
 */
@FunctionalInterface
public interface ProductionListener {
    void onProduced(long tick, ItemType item);
}
