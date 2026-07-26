package com.rustorio.domain.world;

import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.UndergroundBelt;
import java.util.Map;
import java.util.NavigableMap;

/**
 * The two-phase, belt-aware traversal extracted out of {@code World.tick()} (P3-03,
 * BUG_FIX_PROGRESS.md) so the ordering rules live in one focused class instead of being one more
 * responsibility folded into the aggregate root.
 *
 * <p>Everything that prefers the descending pass ({@link Building#prefersDescendingTick()} true —
 * buildings without a facing, and rightward/downward belts) goes first, high coordinates to low;
 * the rest (leftward/upward belts) go afterward, low to high. Each building says which traversal
 * it needs; the scheduler never asks whether something is a belt.
 *
 * <p><b>Closes the P2-07 gap</b>: "at most one tile per tick" held within a single {@link
 * com.rustorio.domain.building.BeltSegment} even before this class existed, but not across a
 * boundary between two segments of different orientation — a descending segment (say, DOWN) could
 * hand cargo to an ascending segment (say, LEFT) which then ticked AGAIN in the same frame's
 * ascending pass and moved that same item a second tile. The fix isn't in the traversal order
 * itself (reordering the two passes can't fix a case that's inherently cross-phase); it's a mark
 * on the receiving tile — {@code Belt} records that it was fed via {@code accept} THIS tick, and
 * {@code BeltSegment.tick} refuses to move a marked tile until the mark is cleared. This class
 * owns clearing it: once, for every belt, before either pass runs.
 *
 * <p>{@link UndergroundBelt} entrances carry the same mark for the same reason (found in a
 * post-Phase-4 review, not an original P-numbered task): {@code direction} alone deciding an
 * entrance's pass (P2-06) means a feeder belt of the OTHER pass can hand it cargo one phase before
 * the entrance's own tick runs, in the same frame — without the mark, that entrance would relay
 * straight to its exit immediately, skipping the settle every other cross-boundary hand-off gets.
 */
final class TickScheduler {

    private TickScheduler() {
    }

    static void tick(NavigableMap<World.Coord, Building> buildings, TickContext context) {
        for (Building building : buildings.values()) {
            Building unwrapped = Building.unwrap(building);
            if (unwrapped instanceof Belt belt) {
                BuildingFactory.clearArrivalMark(belt);
            } else if (unwrapped instanceof UndergroundBelt tunnel) {
                BuildingFactory.clearArrivalMark(tunnel);
            }
        }

        for (Map.Entry<World.Coord, Building> entry : buildings.descendingMap().entrySet()) {
            if (entry.getValue().prefersDescendingTick()) {
                tickEntry(entry, context);
            }
        }
        for (Map.Entry<World.Coord, Building> entry : buildings.entrySet()) {
            if (!entry.getValue().prefersDescendingTick()) {
                tickEntry(entry, context);
            }
        }
    }

    private static void tickEntry(Map.Entry<World.Coord, Building> entry, TickContext context) {
        World.Coord coord = entry.getKey();
        entry.getValue().tick(context, coord.x(), coord.y());
    }
}
