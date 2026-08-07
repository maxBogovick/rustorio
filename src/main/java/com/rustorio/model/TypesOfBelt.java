package com.rustorio.model;

public enum TypesOfBelt {
    BELT("Belt"),
    SPLITTER("Splitter"),
    UNDERGROUND("Underground"),
    BRIDGE("Bridge");

    private final String label;
    TypesOfBelt(String label) { this.label = label; }
    public String label() { return label; }
}
