package com.rustorio;

// com/rustorio/BuildingType.java
public enum BuildingType {
    MINER("Miner"), CHEST("Chest"), FURNACE("Furnace"), BELT("Belt");

    private final String label;
    BuildingType(String label) { this.label = label; }
    public String label() { return label; }
}
