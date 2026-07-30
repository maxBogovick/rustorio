package com.rustorio.core;

public enum Direction {
    NORTH(0, 1, "N"), EAST(1, 0, "E"), SOUTH(0, -1, "S"), WEST(-1, 0, "W");

    public final int dx;
    public final int dy;
    public final String shortName;

    Direction(int dx, int dy, String name) {
        this.dx = dx;
        this.dy = dy;
        this.shortName = name;
    }

    public int dx(){
        return this.dx;
    }            // смещение по X на соседнюю клетку
    public int dy(){
        return this.dy;
    }            // смещение по Y (ось вниз: North = -1)
    public String shortName(){
        return this.shortName;
    }  // "N"/"E"/"S"/"W" — для HUD
    public Direction rotateCw(){
        Direction [] all = values();
        return all[(ordinal() + 1) % all.length];
    } // поворот по часовой (клавиша R)
}
