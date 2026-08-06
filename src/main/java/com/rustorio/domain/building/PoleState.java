package com.rustorio.domain.building;

/**
 * {@link Pole}'s own captured state, which is nothing at all: a pole has no direction, no buffer and
 * no timer, and the grid it belongs to is rebuilt from where the poles stand, exactly as belt
 * segments and fluid networks are. The record exists anyway because every archetype's save row
 * carries a {@code state} value; an empty one is the honest answer, not a missing field.
 */
public record PoleState() {
}
