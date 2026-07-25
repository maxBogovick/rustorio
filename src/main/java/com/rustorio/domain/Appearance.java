package com.rustorio.domain;

/**
 * How a building looks right now: which {@link Sprite}, plus an optional numeric badge (how much
 * is queued in a chest, how hot a furnace's buffer is). Lives in the domain, not the renderer:
 * the building decides its own appearance; drawing it is dumb, uniform work for every building.
 *
 * <p>{@code badge} is a plain {@code int} with a sentinel, not {@code OptionalInt}: Effective Java
 * Item 55 says {@code Optional} belongs on method return types, never a field or record
 * component — see {@code Recipe#input2} for the same reasoning spelled out in full.
 */
public record Appearance(Sprite sprite, int badge) {

    private static final int NO_BADGE = -1;

    public static Appearance of(Sprite sprite) {
        return new Appearance(sprite, NO_BADGE);
    }

    public static Appearance of(Sprite sprite, int badge) {
        return new Appearance(sprite, badge);
    }

    public boolean hasBadge() {
        return badge != NO_BADGE;
    }
}
