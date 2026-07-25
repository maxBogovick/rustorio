package com.rustorio.domain;

/**
 * The building kind a player can pick in the hotbar. Declaration order is hotbar order and
 * keyboard-shortcut order (1, 2, 3…).
 */
public enum BuildingType {
    MINER("Miner"),
    CHEST("Chest"),
    FURNACE("Furnace"),
    BELT("Belt"),
    SPLITTER("Splitter"),
    PRESS("Press"),
    UNDERGROUND_IN("Tunnel in"),
    UNDERGROUND_OUT("Tunnel out"),
    LAB("Lab");

    private final String label;

    BuildingType(String label) {
        this.label = label;
    }

    /** Hotbar caption (ASCII only — the bitmap font has no Cyrillic glyphs). */
    public String label() {
        return label;
    }
}
