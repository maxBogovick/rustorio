package com.rustorio;

public record Appearance(Sprite sprite, int badge) {
    public static final int NO_BADGE = -1;
    public static Appearance of(Sprite sprite)            { return new Appearance(sprite, NO_BADGE); }
    public static Appearance of(Sprite sprite, int badge) { return new Appearance(sprite, badge); }
    public boolean hasBadge() { return badge != NO_BADGE; }
}