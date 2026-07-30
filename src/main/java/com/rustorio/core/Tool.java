package com.rustorio.core;

public enum Tool {
    MINER("Miner", 1),   // слот 1
    BELT("Belt" ,2),    // слот 2
    FURNACE("Furnace", 3), // слот 3
    CHEST("Chest", 4);   // слот 4

    public  final String name;
    public final int index;

    Tool(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String displayName(){
        return this.name;
    } // человекочитаемое имя для HUD
    public int hotkeySlot() {
        return this.index;
    }    // номер слота панели (1..9)
}
