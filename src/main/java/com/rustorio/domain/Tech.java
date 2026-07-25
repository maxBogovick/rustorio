package com.rustorio.domain;

/**
 * A global technology: costs research points, once unlocked applies everywhere on the map (as
 * opposed to a per-building {@code SpeedModule}). Declaration order is unlock order — {@link
 * Research#addPoints} opens technologies strictly by ascending {@link #cost}.
 */
public enum Tech {
    FAST_MINING(8, "Fast mining"),
    FAST_SMELTING(20, "Fast smelting"),
    BIG_BUFFER(35, "Big buffers"),
    LONG_TUNNEL(55, "Long tunnels"),
    FAST_LAB(80, "Fast research");

    private final int cost;
    private final String label;

    Tech(int cost, String label) {
        this.cost = cost;
        this.label = label;
    }

    public int cost() {
        return cost;
    }

    public String label() {
        return label;
    }
}
