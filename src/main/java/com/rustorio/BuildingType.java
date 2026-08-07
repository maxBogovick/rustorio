package com.rustorio;

import com.rustorio.model.TypesOfBelt;

// com/rustorio/BuildingType.java
public enum BuildingType {
    MINER("Miner"),
    CHEST("Chest"),
    FURNACE("Furnace"),
    BELT(TypesOfBelt.BELT.label()),
    SPLITTER(TypesOfBelt.SPLITTER.label());

    private final String label;
    BuildingType(String label) { this.label = label; }
    public String label() { return label; }
}
