package com.rustorio.domain;

/**
 * One map cell's position, ordered by {@code x} first and then {@code y} — the key a {@link
 * com.rustorio.domain.building.FluidNetwork} indexes its own tiles by, so that walking a network
 * (to split it, to hand each tile its share for a save) always visits cells in the same order no
 * matter what order the player built them in or a save restored them in.
 *
 * <p><b>Why this is not {@code World.Coord}</b>, which is the identical record one package over:
 * {@code com.rustorio.domain.building} is architecturally forbidden from depending on {@code
 * com.rustorio.domain.world} (see both packages' {@code package-info}), and a fluid network lives
 * on the building side because the tiles that form it do. Living here, in the innermost ring, this
 * one is reachable from both sides. Collapsing the two into a single type is a worthwhile
 * follow-up, but it would touch the world aggregate and every player action that resolves a click
 * to a cell — a change of its own, not a side effect of adding fluids.
 */
public record Cell(int x, int y) implements Comparable<Cell> {

    @Override
    public int compareTo(Cell other) {
        int byX = Integer.compare(x, other.x);
        return byX != 0 ? byX : Integer.compare(y, other.y);
    }
}
