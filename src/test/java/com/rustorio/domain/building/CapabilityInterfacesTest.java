package com.rustorio.domain.building;

import com.rustorio.domain.Direction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the two new capability interfaces are implemented by exactly the classes the design calls
 * for — before anything actually consumes them. {@link UndergroundBelt} deliberately implements
 * {@link SettlesEachTick} but NOT {@link TransportNode}: it never joins a {@link BeltSegment} — each
 * entrance finds its own exit by direct search instead (see {@link UndergroundBelt#findPartner}).
 */
class CapabilityInterfacesTest {

    @Test
    void allFiveArrivalMarkHoldersImplementSettlesEachTick() {
        assertTrue(new Belt(Direction.RIGHT) instanceof SettlesEachTick);
        assertTrue(new UndergroundBelt(UndergroundBelt.Kind.IN, Direction.RIGHT) instanceof SettlesEachTick);
        assertTrue(new Splitter(Direction.RIGHT) instanceof SettlesEachTick);
        assertTrue(new Filter(Direction.RIGHT, com.rustorio.domain.VanillaItems.IRON_ORE) instanceof SettlesEachTick);
        assertTrue(new Inserter(Direction.RIGHT) instanceof SettlesEachTick);
    }

    @Test
    void onlyBeltImplementsTransportNode() {
        assertTrue(new Belt(Direction.RIGHT) instanceof TransportNode,
                "Belt is the only segment-joining tile today — see BeltSegment");

        // Typed as Building, not the concrete final class: UndergroundBelt/Chest don't implement
        // TransportNode, so a same-typed instanceof would be a compile-time-impossible cast (the
        // compiler statically knows a final class can't gain an interface it doesn't declare) —
        // going through the Building supertype makes this the intended RUNTIME check instead.
        Building tunnel = new UndergroundBelt(UndergroundBelt.Kind.IN, Direction.RIGHT);
        assertFalse(tunnel instanceof TransportNode,
                "UndergroundBelt never joins a BeltSegment — it finds its partner by direct search (findPartner), not segment membership");
        Building chest = new Chest();
        assertFalse(chest instanceof TransportNode);
    }
}
